package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.source.FakeRecentFilesSource
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
