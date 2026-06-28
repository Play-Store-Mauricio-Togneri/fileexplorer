package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.source.FakeRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RecentFilesRepositoryTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "test_recent_files_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `recentFilesFlow filters out non-existing files`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val files = listOf(
            RecentFile("/non/existing/path.txt", "path.txt", "text/plain", 1000L),
            RecentFile(existingFile.absolutePath, "existing.txt", "text/plain", 2000L)
        )
        val repository = RecentFilesRepository(FakeRecentFilesSource(files))

        val result = repository.recentFilesFlow.first()

        assertEquals(1, result.size)
        assertEquals(existingFile.absolutePath, result[0].path)
    }

    @Test
    fun `getRecentFiles filters out non-existing files`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val files = listOf(
            RecentFile("/non/existing/path.txt", "path.txt", "text/plain", 1000L),
            RecentFile(existingFile.absolutePath, "existing.txt", "text/plain", 2000L)
        )
        val repository = RecentFilesRepository(FakeRecentFilesSource(files))

        val result = repository.getRecentFiles()

        assertEquals(1, result.size)
        assertEquals(existingFile.absolutePath, result[0].path)
    }

    @Test
    fun `addRecentFile adds new file to the list`() = runTest {
        val source = FakeRecentFilesSource()
        val repository = RecentFilesRepository(source)
        val file = createTempFile("new.txt")

        repository.addRecentFile(file)

        val saved = source.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(file.absolutePath, saved[0].path)
    }

    @Test
    fun `addRecentFile moves existing file to top`() = runTest {
        val file1 = createTempFile("file1.txt")
        val file2 = createTempFile("file2.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(file1.absolutePath, "file1.txt", "text/plain", 1000L),
                RecentFile(file2.absolutePath, "file2.txt", "text/plain", 2000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.addRecentFile(file2)

        val saved = source.getRecentFiles()
        assertEquals(2, saved.size)
        assertEquals(file2.absolutePath, saved[0].path)
        assertEquals(file1.absolutePath, saved[1].path)
    }

    @Test
    fun `addRecentFile ignores directories`() = runTest {
        val source = FakeRecentFilesSource()
        val repository = RecentFilesRepository(source)
        val dir = File(tempDir, "subdir").apply { mkdirs() }

        repository.addRecentFile(dir)

        val saved = source.getRecentFiles()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `addRecentFile trims list to max size`() = runTest {
        val files = (1..20).map { i ->
            RecentFile(createTempFile("file$i.txt").absolutePath, "file$i.txt", "text/plain", i.toLong())
        }
        val source = FakeRecentFilesSource(files)
        val repository = RecentFilesRepository(source)
        val newFile = createTempFile("new.txt")

        repository.addRecentFile(newFile)

        val saved = source.getRecentFiles()
        assertEquals(20, saved.size)
        assertEquals(newFile.absolutePath, saved[0].path)
    }

    @Test
    fun `removeRecentFile removes file from list`() = runTest {
        val file1 = createTempFile("file1.txt")
        val file2 = createTempFile("file2.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(file1.absolutePath, "file1.txt", "text/plain", 1000L),
                RecentFile(file2.absolutePath, "file2.txt", "text/plain", 2000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.removeRecentFile(file1.absolutePath)

        val saved = source.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(file2.absolutePath, saved[0].path)
    }

    @Test
    fun `updatePath updates the renamed recent file path and name`() = runTest {
        // The on-disk rename already happened; only the new path exists.
        val renamedFile = createTempFile("bar.txt")
        val oldPath = File(tempDir, "foo.txt").absolutePath
        val source = FakeRecentFilesSource(
            listOf(RecentFile(oldPath, "foo.txt", "text/plain", 1000L))
        )
        val repository = RecentFilesRepository(source)

        repository.updatePath(oldPath, renamedFile.absolutePath)

        val saved = repository.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(renamedFile.absolutePath, saved[0].path)
        assertEquals("bar.txt", saved[0].name)
    }

    @Test
    fun `updatePath rewrites recents inside a renamed folder`() = runTest {
        // Folder renamed on disk: the recent child now lives under the new folder name.
        val newDir = File(tempDir, "Documents").apply { mkdirs() }
        val renamedChild = File(newDir, "foo.txt").apply { writeText("test content") }
        val oldDir = File(tempDir, "Docs").absolutePath
        val oldChildPath = File(tempDir, "Docs/foo.txt").absolutePath
        val source = FakeRecentFilesSource(
            listOf(RecentFile(oldChildPath, "foo.txt", "text/plain", 1000L))
        )
        val repository = RecentFilesRepository(source)

        repository.updatePath(oldDir, newDir.absolutePath)

        val saved = repository.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(renamedChild.absolutePath, saved[0].path)
        assertEquals("foo.txt", saved[0].name)
    }

    @Test
    fun `updatePath refreshes the mime type of a renamed recent file`() = runTest {
        // Renaming can change the extension; the stored type must follow the new name (the type
        // flags isImage/isPdf/etc. read mimeType with no name fallback). MimeTypeMap is unavailable
        // in JVM tests, so assert against the same util the production code uses.
        val renamedFile = createTempFile("clip.gif")
        val oldPath = File(tempDir, "clip.txt").absolutePath
        val source = FakeRecentFilesSource(
            listOf(RecentFile(oldPath, "clip.txt", "text/plain", 1000L))
        )
        val repository = RecentFilesRepository(source)

        repository.updatePath(oldPath, renamedFile.absolutePath)

        val saved = source.getRecentFiles()
        assertEquals(MimeTypeUtil.getMimeType(renamedFile), saved[0].mimeType)
    }

    @Test
    fun `updatePath leaves sibling-prefixed recents untouched and skips the write`() = runTest {
        // "/x/Docs" rename must not match the sibling "/x/DocsBackup/...".
        val source = FakeRecentFilesSource(
            listOf(RecentFile("/x/DocsBackup/foo.txt", "foo.txt", "text/plain", 1000L))
        )
        val repository = RecentFilesRepository(source)

        repository.updatePath("/x/Docs", "/x/Documents")

        val saved = source.getRecentFiles()
        assertEquals("/x/DocsBackup/foo.txt", saved[0].path)
        assertEquals(0, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles removes entries whose files are missing`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile("/non/existing/path.txt", "path.txt", "text/plain", 1000L),
                RecentFile(existingFile.absolutePath, "existing.txt", "text/plain", 2000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.pruneNonExistentFiles()

        val saved = source.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(existingFile.absolutePath, saved[0].path)
        assertEquals(1, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles keeps the list and skips the write when all files exist`() = runTest {
        val file1 = createTempFile("file1.txt")
        val file2 = createTempFile("file2.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(file1.absolutePath, "file1.txt", "text/plain", 1000L),
                RecentFile(file2.absolutePath, "file2.txt", "text/plain", 2000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.pruneNonExistentFiles()

        val saved = source.getRecentFiles()
        assertEquals(2, saved.size)
        assertEquals(0, source.updateCount)
    }

    @Test
    fun `clearRecentFiles empties the list`() = runTest {
        val file = createTempFile("file.txt")
        val source = FakeRecentFilesSource(
            listOf(RecentFile(file.absolutePath, "file.txt", "text/plain", 1000L))
        )
        val repository = RecentFilesRepository(source)

        repository.clearRecentFiles()

        val saved = source.getRecentFiles()
        assertTrue(saved.isEmpty())
    }

    private fun createTempFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("test content")
        return file
    }
}
