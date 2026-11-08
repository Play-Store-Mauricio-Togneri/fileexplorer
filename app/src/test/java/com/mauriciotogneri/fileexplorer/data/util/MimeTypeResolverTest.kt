package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Covers the I/O around [MimeTypeSniffer]: which files are read at all, what a read that fails or
 * stalls gives, and the ZIP refinement, on real files.
 *
 * The rule these tests exist for is that a file whose name gives a type is never read: opening a
 * named file must behave exactly as it did before contents were read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MimeTypeResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `never reads a file whose name gives a type`() = runTest {
        var reads = 0

        val resolved = resolver {
            reads++
            SniffOutcome.Match("image/jpeg")
        }.resolve("/nonexistent/photo.png", "image/png")

        assertEquals(ResolvedType("image/png", TypeSource.EXTENSION), resolved)
        assertEquals(0, reads)
    }

    @Test
    fun `never reads an app bundle, so its installer is still offered`() = runTest {
        // Typed from its contents, a bundle is a plain ZIP, and a tap would open the extract dialog.
        var reads = 0
        val resolver = resolver {
            reads++
            SniffOutcome.Match("application/zip")
        }

        val paths = listOf("/x/game.xapk", "/x/split.apks", "/x/app.apkm", "/x/GAME.XAPK")
        val bundles = paths.map { path -> resolver.resolve(path, ANY) }

        assertEquals(List(paths.size) { ResolvedType(ANY, TypeSource.NONE) }, bundles)
        assertEquals(0, reads)
    }

    @Test
    fun `a viewer never reads an app bundle either`() {
        // A real ZIP on disk, so a read would turn it into application/zip.
        val bundle = zip("game.xapk", "manifest.json" to "{}", "base.apk" to "")
        val item = FileItem(
            path = bundle.absolutePath,
            name = bundle.name,
            isDirectory = false,
            size = bundle.length(),
            lastModified = 0L,
            createdTime = 0L,
            mimeType = ANY
        )

        assertSame(item, MimeTypeResolver.resolveNow(item))
    }

    @Test
    fun `types an extensionless JPEG from its contents`() = runTest {
        val file = file("photo", JPEG)

        assertEquals(ResolvedType("image/jpeg", TypeSource.CONTENT), resolver().resolve(file.path, ANY))
    }

    @Test
    fun `types an extensionless text file from its contents`() = runTest {
        val file = file("README", "Read me first.\nThen build.\n".toByteArray())

        assertEquals(ResolvedType("text/plain", TypeSource.CONTENT), resolver().resolve(file.path, ANY))
    }

    @Test
    fun `leaves contents that match nothing as any type`() = runTest {
        val file = file("blob", ByteArray(4096) { it.toByte() })

        assertEquals(ResolvedType(ANY, TypeSource.NONE), resolver().resolve(file.path, ANY))
    }

    @Test
    fun `leaves an empty file as any type`() = runTest {
        val file = file("empty", ByteArray(0))

        assertEquals(ResolvedType(ANY, TypeSource.NONE), resolver().resolve(file.path, ANY))
    }

    @Test
    fun `reports a missing file or a directory as unread, without a non-fatal`() = runTest {
        val missing = File(temporaryFolder.root, "gone")
        val directory = temporaryFolder.newFolder("photos")

        assertEquals(ResolvedType(ANY, TypeSource.UNREAD), resolver().resolve(missing.path, ANY))
        assertEquals(ResolvedType(ANY, TypeSource.UNREAD), resolver().resolve(directory.path, ANY))
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `reads a file whose name only says it is binary`() = runTest {
        val pdf = file("data.bin", "%PDF-1.7\n%âã\n".toByteArray(Charsets.ISO_8859_1))
        val random = file("other.bin", ByteArray(4096) { (it * 7).toByte() })

        assertEquals(
            ResolvedType("application/pdf", TypeSource.CONTENT),
            resolver().resolve(pdf.path, OCTET_STREAM)
        )
        assertEquals(
            ResolvedType(OCTET_STREAM, TypeSource.EXTENSION),
            resolver().resolve(random.path, OCTET_STREAM)
        )
    }

    @Test
    fun `tells the members of the ZIP family apart by their entries`() = runTest {
        val book = zip("book", "mimetype" to "application/epub+zip", "META-INF/container.xml" to "<container/>")
        val letter = zip("letter", "[Content_Types].xml" to "<Types/>", "_rels/.rels" to "", "word/document.xml" to "")
        val app = zip("app", "AndroidManifest.xml" to "", "classes.dex" to "", "resources.arsc" to "")
        val archive = zip("archive", "a.txt" to "a", "b/c.png" to "")
        val broken = file("broken", archive.readBytes().copyOf(64))

        assertEquals("application/epub+zip", resolver().resolve(book.path, ANY).mimeType)
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            resolver().resolve(letter.path, ANY).mimeType
        )
        assertEquals("application/vnd.android.package-archive", resolver().resolve(app.path, ANY).mimeType)
        assertEquals("application/zip", resolver().resolve(archive.path, ANY).mimeType)
        // A truncated download still opens the extract dialog, which reports the bad archive itself.
        assertEquals(ResolvedType("application/zip", TypeSource.CONTENT), resolver().resolve(broken.path, ANY))
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `reads only the start of a large file`() = runTest {
        val large = file("log", ByteArray(10 * 1024 * 1024) { 'a'.code.toByte() })
        val counting = CountingInputStream(ByteArrayInputStream(large.readBytes()))

        val prefix = MimeTypeResolver.readPrefix(counting)

        assertEquals(ResolvedType("text/plain", TypeSource.CONTENT), resolver().resolve(large.path, ANY))
        assertEquals(MimeTypeSniffer.PREFIX_BYTES, prefix.length)
        assertFalse(prefix.reachedEof)
        assertEquals(MimeTypeSniffer.PREFIX_BYTES.toLong(), counting.count)
    }

    @Test
    fun `stops waiting at the timeout rather than for the read`() = runTest {
        // A stalled read is an uninterruptible syscall, so the fake one ignores cancellation too:
        // bounding the read itself instead of the wait would then hold this call for the whole read.
        val resolved = resolver {
            withContext(NonCancellable) { delay(TIMEOUT_MS * 5) }
            SniffOutcome.Match("image/png")
        }.resolve("/storage/1234-5678/photo", ANY)

        assertEquals(ResolvedType(ANY, TypeSource.UNREAD), resolved)
        // The bound is on the waiting, not on the read: a tap held for the full read of a stalled SD
        // card is the frozen screen the timeout exists to prevent.
        assertEquals(TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `does not report a timeout as a failure`() = runTest {
        resolver {
            delay(TIMEOUT_MS * 5)
            SniffOutcome.Match("image/png")
        }.resolve("/storage/1234-5678/photo", ANY)

        // Drain first, so the abandoned read's cancellation has reached the catch blocks.
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `reports an unexpected failure once, scrubbed, and opens by name`() = runTest {
        val reported = slot<Throwable>()
        every { ErrorReporter.warning(capture(reported), any(), any()) } just Runs

        val resolved = resolver {
            throw IllegalStateException("/storage/emulated/0/Private/photo")
        }.resolve("/storage/emulated/0/Private/photo", ANY)

        assertEquals(ResolvedType(ANY, TypeSource.UNREAD), resolved)
        verify(exactly = 1) { ErrorReporter.warning(any(), "sniff_mime_type", any()) }
        assertFalse(reported.captured.message.orEmpty().contains("Private"))
        assertTrue(reported.captured.message.orEmpty().contains(IllegalStateException::class.java.name))
    }

    @Test
    fun `works out where a type it did not resolve came from`() {
        // On the JVM the platform's name table is absent, so only names the JDK itself knows
        // (txt) give a type; an extensionless name gives the wildcard, as on a device.
        assertEquals(TypeSource.CONTENT, MimeTypeResolver.sourceOf("/x/README", "text/plain"))
        assertEquals(TypeSource.NONE, MimeTypeResolver.sourceOf("/x/README", ANY))
        assertEquals(TypeSource.EXTENSION, MimeTypeResolver.sourceOf("/x/a.txt", "text/plain"))
    }

    private fun file(name: String, bytes: ByteArray): File =
        File(temporaryFolder.root, name).apply { writeBytes(bytes) }

    /** A ZIP of [entries], the first one STORED as EPUB and ODF require of their `mimetype`. */
    private fun zip(name: String, vararg entries: Pair<String, String>): File {
        val file = File(temporaryFolder.root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEachIndexed { index, (entryName, content) ->
                val bytes = content.toByteArray()
                val entry = ZipEntry(entryName)
                if (index == 0) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                out.putNextEntry(entry)
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    /** Runs the read on the test scheduler, so the timeout is spent in virtual time. */
    private fun TestScope.resolver(
        sniff: suspend (File) -> SniffOutcome = { MimeTypeResolver.readAndSniff(it) }
    ) = MimeTypeResolver(
        scope = this,
        dispatcher = StandardTestDispatcher(testScheduler),
        timeoutMs = TIMEOUT_MS,
        sniff = sniff
    )

    private class CountingInputStream(private val input: InputStream) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int = input.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            input.read(b, off, len).also { if (it > 0) count += it }
    }

    private companion object {
        const val TIMEOUT_MS = MimeTypeResolver.TIMEOUT_MS
        const val ANY = "*/*"
        const val OCTET_STREAM = "application/octet-stream"
        val JPEG = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0x00, 0x01
        )
    }
}
