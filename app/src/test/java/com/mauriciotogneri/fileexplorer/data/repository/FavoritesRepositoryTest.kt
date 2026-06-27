package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.source.FakeFavoriteFilesSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FavoritesRepositoryTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "test_favorite_files_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `favoritesFlow filters out non-existing files`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val files = listOf(
            Favorite("/non/existing/path.txt", "path.txt", false, "text/plain", 1000L),
            Favorite(existingFile.absolutePath, "existing.txt", false, "text/plain", 2000L)
        )
        val repository = FavoritesRepository(FakeFavoriteFilesSource(files))

        val result = repository.favoritesFlow.first()

        assertEquals(1, result.size)
        assertEquals(existingFile.absolutePath, result[0].path)
    }

    @Test
    fun `getFavorites filters out non-existing files`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val files = listOf(
            Favorite("/non/existing/path.txt", "path.txt", false, "text/plain", 1000L),
            Favorite(existingFile.absolutePath, "existing.txt", false, "text/plain", 2000L)
        )
        val repository = FavoritesRepository(FakeFavoriteFilesSource(files))

        val result = repository.getFavorites()

        assertEquals(1, result.size)
        assertEquals(existingFile.absolutePath, result[0].path)
    }

    @Test
    fun `addFavorite adds new file to the top`() = runTest {
        val source = FakeFavoriteFilesSource()
        val repository = FavoritesRepository(source)
        val file = createTempFile("new.txt")

        repository.addFavorite(file.absolutePath, file.name, false, "text/plain")

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(file.absolutePath, saved[0].path)
    }

    @Test
    fun `addFavorite keeps directories`() = runTest {
        val source = FakeFavoriteFilesSource()
        val repository = FavoritesRepository(source)
        val dir = File(tempDir, "subdir").apply { mkdirs() }

        repository.addFavorite(dir.absolutePath, dir.name, true, "")

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertTrue(saved[0].isDirectory)
    }

    @Test
    fun `addFavorite moves an existing entry to the top`() = runTest {
        val file1 = createTempFile("file1.txt")
        val file2 = createTempFile("file2.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(file1.absolutePath, "file1.txt", false, "text/plain", 1000L),
                Favorite(file2.absolutePath, "file2.txt", false, "text/plain", 2000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.addFavorite(file2.absolutePath, file2.name, false, "text/plain")

        val saved = source.getFavorites()
        assertEquals(2, saved.size)
        assertEquals(file2.absolutePath, saved[0].path)
        assertEquals(file1.absolutePath, saved[1].path)
    }

    @Test
    fun `addFavorite does not cap the list`() = runTest {
        val files = (1..25).map { i ->
            Favorite(createTempFile("file$i.txt").absolutePath, "file$i.txt", false, "text/plain", i.toLong())
        }
        val source = FakeFavoriteFilesSource(files)
        val repository = FavoritesRepository(source)
        val newFile = createTempFile("new.txt")

        repository.addFavorite(newFile.absolutePath, newFile.name, false, "text/plain")

        val saved = source.getFavorites()
        assertEquals(26, saved.size)
        assertEquals(newFile.absolutePath, saved[0].path)
    }

    @Test
    fun `removeFavorite removes the entry from the list`() = runTest {
        val file1 = createTempFile("file1.txt")
        val file2 = createTempFile("file2.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(file1.absolutePath, "file1.txt", false, "text/plain", 1000L),
                Favorite(file2.absolutePath, "file2.txt", false, "text/plain", 2000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.removeFavorite(file1.absolutePath)

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(file2.absolutePath, saved[0].path)
    }

    @Test
    fun `pruneNonExistentFiles removes entries whose files are missing`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite("/non/existing/path.txt", "path.txt", false, "text/plain", 1000L),
                Favorite(existingFile.absolutePath, "existing.txt", false, "text/plain", 2000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.pruneNonExistentFiles()

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(existingFile.absolutePath, saved[0].path)
        assertEquals(1, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles keeps the list and skips the write when all files exist`() = runTest {
        val file1 = createTempFile("file1.txt")
        val file2 = createTempFile("file2.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(file1.absolutePath, "file1.txt", false, "text/plain", 1000L),
                Favorite(file2.absolutePath, "file2.txt", false, "text/plain", 2000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.pruneNonExistentFiles()

        val saved = source.getFavorites()
        assertEquals(2, saved.size)
        assertEquals(0, source.updateCount)
    }

    @Test
    fun `clearFavorites empties the list`() = runTest {
        val file = createTempFile("file.txt")
        val source = FakeFavoriteFilesSource(
            listOf(Favorite(file.absolutePath, "file.txt", false, "text/plain", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.clearFavorites()

        val saved = source.getFavorites()
        assertTrue(saved.isEmpty())
    }

    private fun createTempFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("test content")
        return file
    }
}
