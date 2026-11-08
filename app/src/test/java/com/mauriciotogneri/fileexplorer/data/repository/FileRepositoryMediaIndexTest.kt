package com.mauriciotogneri.fileexplorer.data.repository

import android.os.StatFs
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.source.FakeMediaIndexSync
import com.mauriciotogneri.fileexplorer.data.source.FakeMediaIndexSync.Call
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * What each operation hands [com.mauriciotogneri.fileexplorer.data.source.MediaIndexSync]: exactly
 * once, however the operation ended, the top-level paths it created and the ones it took away —
 * never a path per file. A failed, partial or cancelled run reports the same roots a clean one
 * would have reached, because the sync reads the disk when it runs.
 *
 * Real temporary files throughout, with the JVM stand-ins `FileRepositoryTest` uses for the
 * [android.system.Os] calls the production defaults make.
 */
class FileRepositoryMediaIndexTest {

    private val sync = FakeMediaIndexSync()
    private lateinit var tempDir: File
    private lateinit var source: File
    private lateinit var target: File
    private val allowedRoots get() = listOf(tempDir.absolutePath)

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "file_repo_media_index_test_${System.nanoTime()}")
        source = File(tempDir, "source").apply { mkdirs() }
        target = File(tempDir, "target").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    // === rename ===

    @Test
    fun `rename reports the new path as created and the old one as removed`() = runTest {
        val file = File(source, "old.jpg").apply { writeText("x") }

        val result = repository().rename(fileItemFor(file), "new.jpg")

        assertNotNull(result)
        assertEquals(
            listOf(Call(listOf(File(source, "new.jpg").absolutePath), listOf(file.absolutePath))),
            sync.calls
        )
    }

    @Test
    fun `a rename that failed still reports once`() = runTest {
        val file = File(source, "old.jpg").apply { writeText("x") }
        File(source, "taken.jpg").writeText("y")

        val result = repository().rename(fileItemFor(file), "taken.jpg")

        assertNull(result)
        assertEquals(
            listOf(Call(listOf(File(source, "taken.jpg").absolutePath), listOf(file.absolutePath))),
            sync.calls
        )
    }

    @Test
    fun `a rename rejected before it touches the disk reports nothing`() = runTest {
        val file = File(source, "old.jpg").apply { writeText("x") }

        assertNull(repository().rename(fileItemFor(file), "a/b.jpg"))

        assertTrue(sync.calls.isEmpty())
    }

    // === createFolder ===

    @Test
    fun `createFolder reports nothing`() = runTest {
        assertTrue(repository().createFolder(target.absolutePath, "New folder"))

        assertTrue(sync.calls.isEmpty())
    }

    // === delete and deleteWithProgress ===

    @Test
    fun `delete reports every selected root as removed and nothing under it`() = runTest {
        val folder = File(source, "folder").apply { mkdirs() }
        File(folder, "inside.jpg").writeText("x")
        val file = File(source, "file.jpg").apply { writeText("y") }

        repository().delete(listOf(fileItemFor(folder), fileItemFor(file)))

        assertEquals(listOf(Call(emptyList(), listOf(folder.absolutePath, file.absolutePath))), sync.calls)
    }

    @Test
    fun `a partly failed delete reports every selected root`() = runTest {
        val kept = File(source, "kept.jpg").apply { writeText("x") }
        val gone = File(source, "gone.jpg").apply { writeText("y") }
        val repository = repository(removeFile = { file ->
            if (file == kept) RemoveOutcome.Failed(ERRNO_UNKNOWN) else deleteOnJvm(file)
        })

        val result = repository.delete(listOf(fileItemFor(kept), fileItemFor(gone)))

        assertEquals(1, result.failedCount)
        assertEquals(listOf(Call(emptyList(), listOf(kept.absolutePath, gone.absolutePath))), sync.calls)
    }

    @Test
    fun `a delete that failed outright still reports once`() = runTest {
        val kept = File(source, "kept.jpg").apply { writeText("x") }
        val repository = repository(removeFile = { RemoveOutcome.Failed(ERRNO_UNKNOWN) })

        val result = repository.delete(listOf(fileItemFor(kept)))

        assertFalse(result.success)
        assertEquals(listOf(Call(emptyList(), listOf(kept.absolutePath))), sync.calls)
    }

    @Test
    fun `deleteWithProgress reports every selected root as removed`() = runTest {
        val folder = File(source, "folder").apply { mkdirs() }
        File(folder, "inside.jpg").writeText("x")
        val file = File(source, "file.jpg").apply { writeText("y") }

        repository().deleteWithProgress(listOf(fileItemFor(folder), fileItemFor(file))).toList()

        assertEquals(listOf(Call(emptyList(), listOf(folder.absolutePath, file.absolutePath))), sync.calls)
    }

    @Test
    fun `a partly failed deleteWithProgress reports every selected root`() = runTest {
        val kept = File(source, "kept.jpg").apply { writeText("x") }
        val gone = File(source, "gone.jpg").apply { writeText("y") }
        val repository = repository(removeFile = { file ->
            if (file == kept) RemoveOutcome.Failed(ERRNO_UNKNOWN) else deleteOnJvm(file)
        })

        val completion = repository.deleteWithProgress(listOf(fileItemFor(kept), fileItemFor(gone))).toList().last()

        assertEquals(1, completion.failedFiles)
        assertEquals(listOf(Call(emptyList(), listOf(kept.absolutePath, gone.absolutePath))), sync.calls)
    }

    @Test
    fun `a cancelled deleteWithProgress still reports every selected root`() = runTest {
        // One emission per node, and more nodes than the flow buffers, so the walk is still going
        // when the collector stops.
        val folder = File(source, "folder").apply { mkdirs() }
        repeat(200) { index -> File(folder, "file_$index.jpg").writeText("x") }
        val file = File(source, "file.jpg").apply { writeText("y") }

        repository().deleteWithProgress(listOf(fileItemFor(folder), fileItemFor(file))).first()

        assertTrue("The cancel must land before the walk ends", file.exists())
        assertEquals(listOf(Call(emptyList(), listOf(folder.absolutePath, file.absolutePath))), sync.calls)
    }

    // === moveByRename ===

    @Test
    fun `moveByRename reports each renamed item's two paths and nothing for the rest`() = runTest {
        val folder = File(source, "folder").apply { mkdirs() }
        File(folder, "inside.jpg").writeText("x")
        val refused = File(source, "refused.jpg").apply { writeText("y") }
        val file = File(source, "file.jpg").apply { writeText("z") }
        val repository = repository(renamePath = { from, to ->
            if (from == refused) throw IOException("EXDEV (Cross-device link)")
            renameOnJvm(from, to)
        })

        val result = repository.moveByRename(
            listOf(folder, refused, file).map { fileItemFor(it) },
            target.absolutePath,
            allowedRoots
        )

        assertEquals(listOf(refused.absolutePath), result.remaining.map { it.path })
        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "folder").absolutePath, File(target, "file.jpg").absolutePath),
                    listOf(folder.absolutePath, file.absolutePath)
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `moveByRename that renames nothing still reports once, with nothing in it`() = runTest {
        val file = File(source, "file.jpg").apply { writeText("x") }
        val repository = repository(renamePath = { _, _ -> throw IOException("EXDEV (Cross-device link)") })

        repository.moveByRename(listOf(fileItemFor(file)), target.absolutePath, allowedRoots)

        assertEquals(listOf(Call(emptyList(), emptyList())), sync.calls)
    }

    @Test
    fun `moveByRename refused before anything moves reports nothing`() = runTest {
        val file = File(source, "file.jpg").apply { writeText("x") }
        val outside = File(System.getProperty("java.io.tmpdir"), "outside_${System.nanoTime()}")

        val thrown = runCatching {
            repository().moveByRename(listOf(fileItemFor(file)), outside.absolutePath, listOf(source.absolutePath))
        }.exceptionOrNull()

        assertTrue(thrown is SecurityException)
        assertTrue(sync.calls.isEmpty())
    }

    // === copyFiles ===

    @Test
    fun `a copy reports each item's top-level destination, numbered or merged as it landed`() = runTest {
        val file = File(source, "photo.jpg").apply { writeText("new") }
        File(target, "photo.jpg").writeText("old")
        val folder = File(source, "Album").apply { mkdirs() }
        File(folder, "a.jpg").writeText("a")
        File(target, "Album").mkdirs()

        repository().copyFiles(
            sources = listOf(fileItemFor(file), fileItemFor(folder)),
            targetDir = target.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        // The file was numbered, so the path reported is the one it was written under; the folder
        // merged into the one already there, so it is reported as that folder, not per file.
        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "photo (1).jpg").absolutePath, File(target, "Album").absolutePath),
                    emptyList()
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `a move by copy reports each source root as removed`() = runTest {
        val file = File(source, "photo.jpg").apply { writeText("x") }
        val folder = File(source, "Album").apply { mkdirs() }
        File(folder, "a.jpg").writeText("a")

        repository().copyFiles(
            sources = listOf(fileItemFor(file), fileItemFor(folder)),
            targetDir = target.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "photo.jpg").absolutePath, File(target, "Album").absolutePath),
                    listOf(file.absolutePath, folder.absolutePath)
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `a partial move reports every root it reached, the skipped one included`() = runTest {
        val kept = File(source, "kept.jpg").apply { writeText("x") }
        // Named by the selection but gone by the walk: skipped, never copied, never deleted.
        val ghost = File(source, "ghost.jpg")

        val completion = repository().copyFiles(
            sources = listOf(fileItemFor(kept), fileItemFor(ghost)),
            targetDir = target.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList().last()

        assertEquals(1, completion.skippedFiles)
        assertEquals(
            listOf(Call(listOf(File(target, "kept.jpg").absolutePath), listOf(kept.absolutePath, ghost.absolutePath))),
            sync.calls
        )
    }

    @Test
    fun `a failed move reports every root it reached`() = runTest {
        val moved = File(source, "moved.jpg").apply { writeText("x") }
        val folder = File(source, "folder").apply { mkdirs() }
        File(folder, "doomed.jpg").writeText("y")
        // A file where the folder's copy has to go: nothing can be created inside it.
        File(target, "folder").writeText("in the way")

        val thrown = runCatching {
            repository().copyFiles(
                sources = listOf(fileItemFor(moved), fileItemFor(folder)),
                targetDir = target.absolutePath,
                deleteAfter = true,
                allowedRoots = allowedRoots
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is DestinationNotWritableException)
        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "moved.jpg").absolutePath, File(target, "folder").absolutePath),
                    listOf(moved.absolutePath, folder.absolutePath)
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `a cancelled move reports every root it reached`() = runTest {
        val small = File(source, "small.jpg").apply { writeText("x") }
        // Big enough that the transfer is still inside the write loop when the collector stops:
        // one emission goes out per buffer written, and the flow buffers a bounded number of them.
        val large = File(source, "large.bin").apply { writeText("X".repeat(600_000)) }

        runCatching {
            repository().copyFiles(
                sources = listOf(fileItemFor(small), fileItemFor(large)),
                targetDir = target.absolutePath,
                deleteAfter = true,
                allowedRoots = allowedRoots
            )
                // The small file emits once; the second emission is the large file's first buffer.
                .drop(1)
                .first()
        }

        assertTrue("A cancelled move must not delete the source it was on", large.exists())
        assertFalse("A cancelled copy removes its truncated destination", File(target, "large.bin").exists())
        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "small.jpg").absolutePath, File(target, "large.bin").absolutePath),
                    listOf(small.absolutePath, large.absolutePath)
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `a copy refused before anything is written reports nothing`() = runTest {
        val file = File(source, "photo.jpg").apply { writeText("x") }
        val outside = File(System.getProperty("java.io.tmpdir"), "outside_${System.nanoTime()}")

        val thrown = runCatching {
            repository().copyFiles(
                sources = listOf(fileItemFor(file)),
                targetDir = outside.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(source.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is SecurityException)
        assertTrue(sync.calls.isEmpty())
    }

    // === compressFiles ===

    @Test
    fun `compressFiles reports the archive as created`() = runTest {
        val file = File(source, "photo.jpg").apply { writeText("x") }

        val completion = repository().compressFiles(
            sources = listOf(fileItemFor(file)),
            targetDir = target.absolutePath,
            zipName = "archive.zip",
            allowedRoots = allowedRoots
        ).toList().last()

        assertEquals(File(target, "archive.zip").absolutePath, completion.outputPath)
        assertEquals(listOf(Call(listOf(File(target, "archive.zip").absolutePath), emptyList())), sync.calls)
    }

    // === uncompressFile ===

    @Test
    fun `an extraction reports each top-level entry once, as the name resolves`() = runTest {
        givenPlentyOfFreeSpace()
        File(target, "readme.txt").writeText("mine")
        val zipFile = zipWithEntries(
            mapOf(
                "photos/a.jpg" to "a",
                "photos/2024/b.jpg" to "b",
                // The "./" several archivers put in front of every entry.
                "./music/c.mp3" to "c",
                "readme.txt" to "theirs"
            )
        )

        repository().uncompressFile(
            zipPath = zipFile.absolutePath,
            targetDir = target.absolutePath,
            allowedRoots = allowedRoots
        ).toList()

        // A file straight in the target is reported under the name it was written to.
        assertEquals(
            listOf(
                Call(
                    listOf("photos", "music", "readme (1).txt").map { File(target, it).absolutePath },
                    emptyList()
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `a failed extraction reports what it wrote and what it rolled back`() = runTest {
        givenPlentyOfFreeSpace()
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/holiday.jpg" to "content"))

        val thrown = runCatching {
            repository().uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = allowedRoots
            ).toList()
        }.exceptionOrNull()

        assertNotNull(thrown)
        assertFalse(File(target, "photos").exists())
        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "photos").absolutePath, File(target, "data.bin").absolutePath),
                    listOf(File(target, "data.bin").absolutePath, File(target, "photos").absolutePath)
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `a cancelled extraction reports what it wrote and what it rolled back`() = runTest {
        givenPlentyOfFreeSpace()
        // Big enough that the extraction is still inside the write loop when the collector stops.
        val zipFile = zipWithEntries(mapOf("photos/holiday.jpg" to "X".repeat(600_000)))

        runCatching {
            repository().uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = allowedRoots
            ).first()
        }

        assertFalse(File(target, "photos").exists())
        // The half-written file is the rollback's first root, then the folder it created.
        assertEquals(
            listOf(
                Call(
                    listOf(File(target, "photos").absolutePath),
                    listOf("photos/holiday.jpg", "photos").map { File(target, it).absolutePath }
                )
            ),
            sync.calls
        )
    }

    @Test
    fun `an extraction refused before anything is written reports nothing`() = runTest {
        val zipFile = zipWithEntries(mapOf("photos/a.jpg" to "a"))
        val outside = File(System.getProperty("java.io.tmpdir"), "outside_${System.nanoTime()}")

        val thrown = runCatching {
            repository().uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = outside.absolutePath,
                allowedRoots = listOf(source.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is SecurityException)
        assertTrue(sync.calls.isEmpty())
    }

    // === helpers ===

    private fun repository(
        removeFile: (File) -> RemoveOutcome = ::deleteOnJvm,
        renamePath: (File, File) -> Unit = ::renameOnJvm
    ) = FileRepository(
        removeFile = removeFile,
        progressEmitIntervalMs = 0L,
        elapsedMillis = { 0L },
        renamePath = renamePath,
        mediaIndexSync = sync
    )

    private fun fileItemFor(file: File) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = file.isDirectory,
        size = file.length(),
        lastModified = file.lastModified(),
        createdTime = file.lastModified(),
        mimeType = "image/jpeg"
    )

    /** The `FileRepositoryTest` stand-in for `removePath`, which goes through a JVM-stubbed `Os`. */
    private fun deleteOnJvm(file: File): RemoveOutcome = when {
        file.delete() -> RemoveOutcome.Removed
        !file.exists() -> RemoveOutcome.AlreadyAbsent
        else -> RemoveOutcome.Failed(ERRNO_UNKNOWN)
    }

    /** The `FileRepositoryTest` stand-in for `Os.rename`: the same `rename(2)`, never a copy. */
    private fun renameOnJvm(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("rename failed")
    }

    private fun givenPlentyOfFreeSpace() {
        // uncompressFile pre-flights the volume with StatFs, an android.* class that throws "not
        // mocked" on the JVM before the extraction under test is ever reached.
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns Long.MAX_VALUE
    }

    private fun zipWithEntries(entries: Map<String, String>): File {
        val zipFile = File(tempDir, "archive.zip")

        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }

        return zipFile
    }

    /**
     * An archive holding [entries] followed by a stored entry, `data.bin`, with one payload byte
     * flipped, so extraction fails on its CRC check after the entries before it have landed and
     * after `data.bin` itself was created — the state the rollback has to undo.
     */
    private fun zipWithEntriesThenCorruptEntry(entries: Map<String, String>): File {
        val payload = PAYLOAD_MARKER.repeat(64).toByteArray()
        val zipFile = File(tempDir, "partial.zip")

        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
            zos.putNextEntry(
                java.util.zip.ZipEntry("data.bin").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    crc = java.util.zip.CRC32().apply { update(payload) }.value
                }
            )
            zos.write(payload)
            zos.closeEntry()
        }

        // Searched from the end: the entries written before the stored payload are deflated, and
        // their compressed bytes could hold the marker too.
        val bytes = zipFile.readBytes()
        val offset = String(bytes, Charsets.ISO_8859_1).lastIndexOf(PAYLOAD_MARKER)
        bytes[offset] = (bytes[offset].toInt() xor 0xFF).toByte()
        zipFile.writeBytes(bytes)

        return zipFile
    }

    private companion object {
        const val PAYLOAD_MARKER = "MEDIA_INDEX_PAYLOAD"
    }
}
