package com.mauriciotogneri.fileexplorer.data.repository

import android.os.StatFs
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Copy, move, compress, extract, list and create-folder scenarios over real temporary files.
 *
 * These used to run as instrumentation tests in `EdgeCasesTest`, `FileOperationsEndToEndTest` and
 * `FolderErrorStatesTest`, costing emulator time on every run while depending on nothing a device
 * provides: none of them renames (the `ATOMIC_MOVE` branch needs a real `SDK_INT`), follows a
 * symlink, or needs the platform's `remove(3)` or `StatFs` to be the real one. The cases those do
 * depend on stayed behind on the device.
 *
 * Deletes go through [deleteOnJvm], the same stand-in `FileRepositoryTest` uses for
 * [android.system.Os], and extraction's free-space pre-flight is answered by [givenPlentyOfFreeSpace].
 */
class FileRepositoryOperationsTest {

    private val repository = FileRepository(
        removeFile = ::deleteOnJvm,
        progressEmitIntervalMs = 0L,
        elapsedMillis = { 0L }
    )

    private lateinit var tempDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "file_repo_ops_test_${System.nanoTime()}")
        sourceDir = File(tempDir, "source").apply { mkdirs() }
        targetDir = File(tempDir, "target").apply { mkdirs() }
        allowedRoots = listOf(tempDir.absolutePath)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    // === createFolder name validation ===

    @Test
    fun `createFolder refuses an empty name`() = runTest {
        assertFalse(repository.createFolder(sourceDir.absolutePath, ""))
    }

    @Test
    fun `createFolder refuses the dot and dot-dot names`() = runTest {
        listOf(".", "..").forEach { name ->
            assertFalse("createFolder must refuse \"$name\"", repository.createFolder(sourceDir.absolutePath, name))
        }
        assertEquals("Nothing may be created next to the source", setOf("source", "target"), tempDir.list()?.toSet())
    }

    @Test
    fun `createFolder accepts a whitespace-only name`() = runTest {
        // Pins the repository as it is, not as the dialogs would have it: `isValidFileName` refuses
        // a blank name before the repository is ever asked, but the repository itself does not, and
        // "   " is a legal name on every filesystem the app writes to.
        assertTrue(repository.createFolder(sourceDir.absolutePath, "   "))
        assertTrue(File(sourceDir, "   ").isDirectory)
    }

    // === Listing ===

    @Test
    fun `listFiles finds the file inside a folder whose name holds URL-significant characters`() = runTest {
        listOf("folder with spaces", "folder#name", "A & B", "folder (copy)").forEach { folderName ->
            val folder = File(sourceDir, folderName).apply { mkdirs() }
            createTestFile(folder, "test.txt", "content")

            val listed = repository.listFiles(folder.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)

            assertEquals("Listing \"$folderName\"", listOf("test.txt"), listed.map { it.name })
        }
    }

    @Test
    fun `listFiles reaches a folder twenty levels deep`() = runTest {
        var current = sourceDir
        repeat(20) { current = File(current, "level${it + 1}").apply { mkdirs() } }
        createTestFile(current, "deep_file.txt", "deep content")

        val listed = repository.listFiles(current.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)

        assertEquals(listOf("deep_file.txt"), listed.map { it.name })
    }

    @Test
    fun `listFiles lists every one of two hundred files`() = runTest {
        repeat(200) { createTestFile(sourceDir, "file_${it + 1}.txt", "content $it") }

        val listed = repository.listFiles(sourceDir.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)

        assertEquals(200, listed.size)
    }

    // === Copy ===

    @Test
    fun `copyFiles copies a folder whose name holds URL-significant characters`() = runTest {
        val folder = File(sourceDir, "folder #1 (copy) & more").apply { mkdirs() }
        createTestFile(folder, "test.txt", "content")

        copy(listOf(folder))

        assertEquals("content", File(targetDir, "folder #1 (copy) & more/test.txt").readText())
    }

    @Test
    fun `copyFiles copies a file with a long name`() = runTest {
        val longName = "B".repeat(100) + ".txt"
        val source = createTestFile(sourceDir, longName, "test content")

        copy(listOf(source))

        assertEquals("test content", File(targetDir, longName).readText())
    }

    @Test
    fun `copyFiles copies a file from ten folders deep to the top of the target`() = runTest {
        var current = sourceDir
        repeat(10) { current = File(current, "nested${it + 1}").apply { mkdirs() } }
        val deep = createTestFile(current, "deep.txt", "content")

        copy(listOf(deep))

        assertEquals("content", File(targetDir, "deep.txt").readText())
    }

    @Test
    fun `copyFiles recreates a nested folder structure and its file`() = runTest {
        val level1 = File(sourceDir, "L1")
        createTestFile(File(level1, "L2/L3"), "deep.txt", "content")

        copy(listOf(level1))

        assertTrue(File(targetDir, "L1/L2/L3").isDirectory)
        assertEquals("content", File(targetDir, "L1/L2/L3/deep.txt").readText())
    }

    @Test
    fun `copyFiles copies every file of a multi-selection`() = runTest {
        val files = listOf("multi1.txt" to "one", "multi2.txt" to "two", "multi3.txt" to "three")
            .map { (name, content) -> createTestFile(sourceDir, name, content) }

        copy(files)

        assertEquals("one", File(targetDir, "multi1.txt").readText())
        assertEquals("two", File(targetDir, "multi2.txt").readText())
        assertEquals("three", File(targetDir, "multi3.txt").readText())
    }

    @Test
    fun `copyFiles copies a selection mixing a file and a folder`() = runTest {
        val file = createTestFile(sourceDir, "standalone.txt", "standalone content")
        val folder = File(sourceDir, "MyFolder")
        createTestFile(folder, "inside.txt", "inside content")

        copy(listOf(file, folder))

        assertEquals("standalone content", File(targetDir, "standalone.txt").readText())
        assertEquals("inside content", File(targetDir, "MyFolder/inside.txt").readText())
    }

    @Test
    fun `copyFiles numbers past every copy already in the target`() = runTest {
        val source = createTestFile(sourceDir, "file.txt", "new")
        createTestFile(targetDir, "file.txt", "v0")
        createTestFile(targetDir, "file (1).txt", "v1")
        createTestFile(targetDir, "file (2).txt", "v2")

        copy(listOf(source))

        assertEquals("new", File(targetDir, "file (3).txt").readText())
        assertEquals("v0", File(targetDir, "file.txt").readText())
        assertEquals("v1", File(targetDir, "file (1).txt").readText())
        assertEquals("v2", File(targetDir, "file (2).txt").readText())
    }

    @Test
    fun `copyFiles copies an empty file as an empty file`() = runTest {
        val empty = File(sourceDir, "empty.txt").apply { createNewFile() }

        copy(listOf(empty))

        val copied = File(targetDir, "empty.txt")
        assertTrue(copied.exists())
        assertEquals(0L, copied.length())
    }

    @Test
    fun `copyFiles copies an empty folder as an empty folder`() = runTest {
        val empty = File(sourceDir, "EmptyFolder").apply { mkdirs() }

        copy(listOf(empty))

        val copied = File(targetDir, "EmptyFolder")
        assertTrue(copied.isDirectory)
        assertEquals(0, copied.list()?.size)
    }

    @Test
    fun `copyFiles reports every byte of a one megabyte file`() = runTest {
        // Deliberately untimed: a wall-clock bound measures the machine, not the copy.
        val content = "X".repeat(1_000_000)
        val source = createTestFile(sourceDir, "large.txt", content)

        val progress = copy(listOf(source))

        val last = progress.last()
        assertTrue(last.isComplete)
        assertTrue("A multi-buffer copy should report progress along the way", progress.size > 1)
        assertEquals(content.length.toLong(), last.totalBytes)
        assertEquals(content.length.toLong(), last.copiedBytes)
        assertEquals(source.length(), File(targetDir, "large.txt").length())
    }

    @Test
    fun `copyFiles names the file it is copying while it runs`() = runTest {
        val source = createTestFile(sourceDir, "tracked.txt", "content")

        val inProgress = copy(listOf(source)).dropLast(1)

        assertTrue(inProgress.isNotEmpty())
        assertTrue(inProgress.any { it.currentFile == "tracked.txt" })
    }

    /**
     * The device-only twin `FileRepositoryTest` lacks: cancelling while the second file is half
     * written has to remove that half-file and keep the first, which had already arrived whole.
     */
    @Test
    fun `a copy cancelled during its second file keeps the first and drops the half-written second`() = runTest {
        val content = "X".repeat(300_000)
        val first = createTestFile(sourceDir, "first.txt", content)
        val second = createTestFile(sourceDir, "second.txt", content)
        var cancelled = false

        launch {
            try {
                // Without buffer(0) the producer runs up to 64 chunks ahead of this collector, and the
                // ~37 chunks left of second.txt fit inside that: the copy could finish before the
                // cancel lands and leave the whole file behind.
                repository.copyFiles(
                    sources = listOf(FileItem.from(first), FileItem.from(second)),
                    targetDir = targetDir.absolutePath,
                    deleteAfter = false,
                    allowedRoots = allowedRoots
                ).buffer(0).collect { progress ->
                    if (progress.copiedFiles >= 1 && progress.currentFile == "second.txt") {
                        cancel("Test cancellation")
                    }
                }
            } catch (_: CancellationException) {
                cancelled = true
            }
        }.join()

        assertTrue("The copy should have been cancelled mid-way", cancelled)
        assertEquals(content, File(targetDir, "first.txt").readText())
        assertFalse(File(targetDir, "second.txt").exists())
    }

    // === Move ===

    @Test
    fun `a move onto a taken name numbers the moved file and removes the source`() = runTest {
        val source = createTestFile(sourceDir, "conflict.txt", "moved content")
        createTestFile(targetDir, "conflict.txt", "existing")

        copy(listOf(source), deleteAfter = true)

        assertEquals("existing", File(targetDir, "conflict.txt").readText())
        assertEquals("moved content", File(targetDir, "conflict (1).txt").readText())
        assertFalse(source.exists())
    }

    @Test
    fun `moving an empty folder recreates it and removes the source`() = runTest {
        val empty = File(sourceDir, "MoveEmpty").apply { mkdirs() }

        copy(listOf(empty), deleteAfter = true)

        assertTrue(File(targetDir, "MoveEmpty").isDirectory)
        assertFalse(empty.exists())
    }

    // === Compress ===

    @Test
    fun `compressFiles archives top-level files under their own names`() = runTest {
        val file1 = createTestFile(sourceDir, "file1.txt", "content 1")
        val file2 = createTestFile(sourceDir, "file2.txt", "content 2")

        val progress = compress(listOf(file1, file2), "archive.zip")

        val last = progress.last()
        assertTrue(last.isComplete)
        assertEquals(2, last.totalFiles)
        assertEquals(2, last.compressedFiles)
        // Opened rather than checked for existence: an archive with the wrong entry names, or none,
        // still exists and still ends in .zip.
        ZipFile(last.outputPath!!).use { zip ->
            assertEquals(setOf("file1.txt", "file2.txt"), zip.fileHeaders.map { it.fileName }.toSet())
            val entry = zip.getFileHeader("file1.txt")
            assertEquals("content 1", zip.getInputStream(entry).use { it.readBytes().decodeToString() })
        }
    }

    @Test
    fun `compressFiles counts every file of a multi-selection`() = runTest {
        val files = (1..5).map { createTestFile(sourceDir, "compress_$it.txt", "content $it") }

        val progress = compress(files, "multi.zip")

        assertTrue("Five files should report progress along the way", progress.size > 1)
        assertTrue(progress.last().isComplete)
        assertEquals(5, progress.last().compressedFiles)
    }

    @Test
    fun `compressFiles archives an empty folder as its own entry`() = runTest {
        val empty = File(sourceDir, "EmptyToCompress").apply { mkdirs() }

        val last = compress(listOf(empty), "empty_folder.zip").last()

        assertTrue(last.isComplete)
        ZipFile(last.outputPath!!).use { zip ->
            assertEquals(listOf("EmptyToCompress/"), zip.fileHeaders.map { it.fileName })
        }
    }

    @Test
    fun `compressFiles refuses a target outside the allowed roots`() = runTest {
        val source = createTestFile(sourceDir, "compress_source.txt", "content")
        val outside = File(System.getProperty("java.io.tmpdir"), "outside_${System.nanoTime()}")

        val thrown = runCatching {
            repository.compressFiles(
                sources = listOf(FileItem.from(source)),
                targetDir = outside.absolutePath,
                zipName = "test.zip",
                allowedRoots = allowedRoots
            ).toList()
        }.exceptionOrNull()

        assertTrue("Expected SecurityException, got $thrown", thrown is SecurityException)
        assertFalse(File(outside, "test.zip").exists())
    }

    // === Extract ===

    @Test
    fun `uncompressFile extracts a password-protected archive with the right password`() = runTest {
        givenPlentyOfFreeSpace()
        val zipPath = createZip("protected.zip", mapOf("secret.txt" to "confidential"), password = "secret123")

        val last = extract(zipPath, password = "secret123").last()

        assertTrue(last.isComplete)
        assertEquals("confidential", File(targetDir, "secret.txt").readText())
    }

    /**
     * Not just that the extraction throws: the file it had started writing before the password
     * check failed must not survive in the user's folder. A leftover there is indistinguishable
     * from a real extraction, and the retry with the right password would land beside it as
     * `test (1).txt`.
     */
    @Test
    fun `a wrong password fails the extraction and leaves nothing in the target`() = runTest {
        givenPlentyOfFreeSpace()
        val zipPath = createZip("password_test.zip", mapOf("test.txt" to "content"), password = "correct123")

        val thrown = runCatching { extract(zipPath, password = "wrong456") }.exceptionOrNull()

        assertTrue("Expected zip4j's ZipException, got $thrown", thrown is ZipException)
        assertEquals(emptyList<String>(), targetDir.list()?.toList())
    }

    @Test
    fun `a missing password fails the extraction and leaves nothing in the target`() = runTest {
        givenPlentyOfFreeSpace()
        val zipPath = createZip("protected_no_pw.zip", mapOf("secret.txt" to "content"), password = "the_password")

        val thrown = runCatching { extract(zipPath, password = null) }.exceptionOrNull()

        assertTrue("Expected zip4j's ZipException, got $thrown", thrown is ZipException)
        assertEquals(emptyList<String>(), targetDir.list()?.toList())
    }

    @Test
    fun `uncompressFile numbers an entry whose name is already taken`() = runTest {
        givenPlentyOfFreeSpace()
        createTestFile(targetDir, "existing.txt", "already here")
        val zipPath = createZip("conflict.zip", mapOf("existing.txt" to "from zip"))

        extract(zipPath)

        assertEquals("already here", File(targetDir, "existing.txt").readText())
        assertEquals("from zip", File(targetDir, "existing (1).txt").readText())
    }

    @Test
    fun `uncompressFile counts every extracted file`() = runTest {
        givenPlentyOfFreeSpace()
        val zipPath = createZip(
            "progress_test.zip",
            mapOf("file1.txt" to "content 1", "file2.txt" to "content 2", "file3.txt" to "content 3")
        )

        val progress = extract(zipPath)

        assertTrue(progress.size > 1)
        assertTrue(progress.last().isComplete)
        assertEquals(3, progress.last().extractedFiles)
    }

    /**
     * `FileRepositoryTest` pins that a cancelled extraction *reports* what it rolled back; this pins
     * what is left on disk, after one file finished and the next was half-written.
     */
    @Test
    fun `a cancelled extraction leaves no extracted or partial file behind`() = runTest {
        givenPlentyOfFreeSpace()
        val content = "X".repeat(300_000)
        val zipPath = createZip(
            "cancel_extract.zip",
            mapOf("file1.txt" to content, "file2.txt" to content, "file3.txt" to content)
        )
        var cancelled = false

        launch {
            try {
                repository.uncompressFile(
                    zipPath = zipPath,
                    targetDir = targetDir.absolutePath,
                    password = null,
                    allowedRoots = allowedRoots
                ).collect { progress ->
                    if (progress.extractedFiles >= 1) {
                        cancel("Test cancellation")
                    }
                }
            } catch (_: CancellationException) {
                cancelled = true
            }
        }.join()

        assertTrue("The extraction should have been cancelled mid-way", cancelled)
        val leftovers = targetDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Leftover files: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `uncompressFile refuses a target outside the allowed roots`() = runTest {
        val zipPath = createZip("test.zip", mapOf("file.txt" to "content"))
        val outside = File(System.getProperty("java.io.tmpdir"), "outside_${System.nanoTime()}")

        val thrown = runCatching {
            repository.uncompressFile(
                zipPath = zipPath,
                targetDir = outside.absolutePath,
                password = null,
                allowedRoots = allowedRoots
            ).toList()
        }.exceptionOrNull()

        assertTrue("Expected SecurityException, got $thrown", thrown is SecurityException)
        assertFalse(outside.exists())
    }

    // === Helpers ===

    private suspend fun copy(sources: List<File>, deleteAfter: Boolean = false): List<CopyProgress> =
        repository.copyFiles(
            sources = sources.map { FileItem.from(it) },
            targetDir = targetDir.absolutePath,
            deleteAfter = deleteAfter,
            allowedRoots = allowedRoots
        ).toList()

    private suspend fun compress(sources: List<File>, zipName: String): List<CompressProgress> =
        repository.compressFiles(
            sources = sources.map { FileItem.from(it) },
            targetDir = targetDir.absolutePath,
            zipName = zipName,
            allowedRoots = allowedRoots
        ).toList()

    private suspend fun extract(zipPath: String, password: String? = null): List<UncompressProgress> =
        repository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = password,
            allowedRoots = allowedRoots
        ).toList()

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    /** An archive in [sourceDir] holding [entries], AES-encrypted when [password] is given. */
    private fun createZip(name: String, entries: Map<String, String>, password: String? = null): String {
        val zipFile = File(sourceDir, name)
        val staging = File(tempDir, "staging_${System.nanoTime()}").apply { mkdirs() }

        try {
            entries.forEach { (entryName, content) -> createTestFile(staging, entryName, content) }

            val zip = if (password == null) ZipFile(zipFile) else ZipFile(zipFile, password.toCharArray())
            zip.use {
                val params = ZipParameters().apply {
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                    if (password != null) {
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.AES
                    }
                }
                staging.listFiles()?.forEach { file ->
                    if (file.isDirectory) it.addFolder(file, params) else it.addFile(file, params)
                }
            }
        } finally {
            staging.deleteRecursively()
        }

        return zipFile.absolutePath
    }

    private fun givenPlentyOfFreeSpace() {
        // uncompressFile pre-flights the volume with StatFs, an android.* class that throws "not
        // mocked" on the JVM before the extraction under test is ever reached.
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns Long.MAX_VALUE
    }

    /** The `FileRepositoryTest` stand-in for `removePath`, which goes through a JVM-stubbed `Os`. */
    private fun deleteOnJvm(file: File): RemoveOutcome = when {
        file.delete() -> RemoveOutcome.Removed
        !file.exists() -> RemoveOutcome.AlreadyAbsent
        else -> RemoveOutcome.Failed(ERRNO_UNKNOWN)
    }
}
