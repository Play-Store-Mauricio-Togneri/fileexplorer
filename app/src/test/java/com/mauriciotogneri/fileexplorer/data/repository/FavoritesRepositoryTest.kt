package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.source.FakeFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `favoritesFlow deduplicates entries sharing a path`() = runTest {
        // Heals stores already corrupted by the pre-fix updatePath: two entries at the same
        // existing path must surface as one, or the path-keyed home LazyRow crashes on duplicate
        // keys.
        val file = createTempFile("dup.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(file.absolutePath, "dup.txt", false, "text/plain", 2000L),
                Favorite(file.absolutePath, "dup.txt", false, "text/plain", 1000L)
            )
        )
        val repository = FavoritesRepository(source)

        val result = repository.favoritesFlow.first()

        assertEquals(1, result.size)
        assertEquals(file.absolutePath, result[0].path)
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
    fun `updatePath updates the renamed favorite path and name`() = runTest {
        // The on-disk rename already happened; only the new path exists.
        val renamedFile = createTempFile("bar.txt")
        val oldPath = File(tempDir, "foo.txt").absolutePath
        val source = FakeFavoriteFilesSource(
            listOf(Favorite(oldPath, "foo.txt", false, "text/plain", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.updatePath(oldPath, renamedFile.absolutePath)

        val saved = repository.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(renamedFile.absolutePath, saved[0].path)
        assertEquals("bar.txt", saved[0].name)
    }

    @Test
    fun `updatePath rewrites favorites inside a renamed folder`() = runTest {
        // Folder renamed on disk: the favorited child now lives under the new folder name.
        val newDir = File(tempDir, "Documents").apply { mkdirs() }
        val renamedChild = File(newDir, "foo.txt").apply { writeText("test content") }
        val oldDir = File(tempDir, "Docs").absolutePath
        val oldChildPath = File(tempDir, "Docs/foo.txt").absolutePath
        val source = FakeFavoriteFilesSource(
            listOf(Favorite(oldChildPath, "foo.txt", false, "text/plain", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.updatePath(oldDir, newDir.absolutePath)

        val saved = repository.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(renamedChild.absolutePath, saved[0].path)
        assertEquals("foo.txt", saved[0].name)
    }

    @Test
    fun `updatePath refreshes the mime type of a renamed favorite`() = runTest {
        // Renaming can change the extension; the stored type must follow the new name (the type
        // flags isImage/isPdf/etc. read mimeType with no name fallback). MimeTypeMap is unavailable
        // in JVM tests, so assert against the same util the production code uses.
        val renamedFile = createTempFile("photo.gif")
        val oldPath = File(tempDir, "photo.txt").absolutePath
        val source = FakeFavoriteFilesSource(
            listOf(Favorite(oldPath, "photo.txt", false, "text/plain", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.updatePath(oldPath, renamedFile.absolutePath)

        val saved = source.getFavorites()
        assertEquals(MimeTypeUtil.getMimeType(renamedFile), saved[0].mimeType)
    }

    @Test
    fun `updatePath keeps the empty mime type of a renamed favorite directory`() = runTest {
        // Directories carry an empty mimeType by convention; recomputing would yield "*/*".
        val renamedDir = File(tempDir, "Documents").apply { mkdirs() }
        val oldPath = File(tempDir, "Docs").absolutePath
        val source = FakeFavoriteFilesSource(
            listOf(Favorite(oldPath, "Docs", true, "", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.updatePath(oldPath, renamedDir.absolutePath)

        val saved = source.getFavorites()
        assertEquals(renamedDir.absolutePath, saved[0].path)
        assertEquals("Documents", saved[0].name)
        assertEquals("", saved[0].mimeType)
    }

    @Test
    fun `updatePath leaves sibling-prefixed favorites untouched and skips the write`() = runTest {
        // "/x/Docs" rename must not match the sibling "/x/DocsBackup/...".
        val source = FakeFavoriteFilesSource(
            listOf(Favorite("/x/DocsBackup/foo.txt", "foo.txt", false, "text/plain", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.updatePath("/x/Docs", "/x/Documents")

        val saved = source.getFavorites()
        assertEquals("/x/DocsBackup/foo.txt", saved[0].path)
        assertEquals(0, source.updateCount)
    }

    @Test
    fun `updatePath collapses a rename that collides with an existing favorite`() = runTest {
        // A stale entry already sits at newPath; renaming a newer entry onto it must not leave two
        // entries sharing a path, which would crash the path-keyed home LazyRow. Entries are ordered
        // most-recent-first (the store's invariant), so distinctBy keeps the freshly-renamed one.
        val oldPath = "/x/draft.docx"
        val newPath = "/x/report.docx"
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(oldPath, "draft.docx", false, "text/plain", 2000L),
                Favorite(newPath, "report.docx", false, "text/plain", 1000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.updatePath(oldPath, newPath)

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(newPath, saved[0].path)
        assertEquals(2000L, saved[0].favoritedTimestamp)
    }

    @Test
    fun `pruneNonExistentFiles removes entries whose files are missing`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(File(tempDir, "path.txt").absolutePath, "path.txt", false, "text/plain", 1000L),
                Favorite(existingFile.absolutePath, "existing.txt", false, "text/plain", 2000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(existingFile.absolutePath, saved[0].path)
        assertEquals(1, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles keeps entries whose volume is not mounted`() = runTest {
        // An ejected SD card answers "does not exist" for every path on it at once. Forgetting them
        // is permanent and reinserting the card would not undo it, so a volume this app cannot see
        // is treated as "cannot say", not as "gone".
        val existingFile = createTempFile("existing.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite("/storage/1234-5678/photo.jpg", "photo.jpg", false, "image/jpeg", 1000L),
                Favorite(existingFile.absolutePath, "existing.txt", false, "text/plain", 2000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

        val saved = source.getFavorites()
        assertEquals(2, saved.size)
        assertEquals("/storage/1234-5678/photo.jpg", saved[0].path)
        assertEquals(0, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles forgets a missing file once its volume is mounted again`() = runTest {
        // The other half of the rule: the entry survives only while its volume is away. Once the
        // card is back and the file is still not on it, it really is gone.
        val source = FakeFavoriteFilesSource(
            listOf(Favorite("/storage/1234-5678/photo.jpg", "photo.jpg", false, "image/jpeg", 1000L))
        )
        val repository = FavoritesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath, "/storage/1234-5678"))

        assertEquals(0, source.getFavorites().size)
        assertEquals(1, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles heals a store holding entries that share a path`() = runTest {
        // Duplicates left by the pre-fix updatePath survive on disk until a write rewrites the
        // store. distinctBy keeps the first, matching what favoritesFlow already surfaces.
        val file = createTempFile("dup.txt")
        val source = FakeFavoriteFilesSource(
            listOf(
                Favorite(file.absolutePath, "dup.txt", false, "text/plain", 2000L),
                Favorite(file.absolutePath, "dup.txt", false, "text/plain", 1000L)
            )
        )
        val repository = FavoritesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

        val saved = source.getFavorites()
        assertEquals(1, saved.size)
        assertEquals(file.absolutePath, saved[0].path)
        assertEquals(2000L, saved[0].favoritedTimestamp)
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

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

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

    // The store persists no modification time — reads stamp it from disk so the home screen can key
    // a favorite's thumbnail exactly as the folder list keys the same file's.
    @Test
    fun `favoritesFlow stamps the file's modification time`() = runTest {
        val file = createTempFile("photo.jpg")
        val repository = FavoritesRepository(
            FakeFavoriteFilesSource(
                listOf(Favorite(file.absolutePath, "photo.jpg", false, "image/jpeg", 1000L))
            )
        )

        val result = repository.favoritesFlow.first()

        assertNotEquals(0L, result[0].lastModified)
        assertEquals(file.lastModified(), result[0].lastModified)
    }

    @Test
    fun `getFavorites stamps the file's modification time`() = runTest {
        val file = createTempFile("photo.jpg")
        val repository = FavoritesRepository(
            FakeFavoriteFilesSource(
                listOf(Favorite(file.absolutePath, "photo.jpg", false, "image/jpeg", 1000L))
            )
        )

        val result = repository.getFavorites()

        assertNotEquals(0L, result[0].lastModified)
        assertEquals(file.lastModified(), result[0].lastModified)
    }

    @Test
    fun `favoritesFlow re-keys a favorite whose file was edited`() = runTest {
        val file = createTempFile("photo.jpg")
        val repository = FavoritesRepository(
            FakeFavoriteFilesSource(
                listOf(Favorite(file.absolutePath, "photo.jpg", false, "image/jpeg", 1000L))
            )
        )
        val before = repository.favoritesFlow.first()[0].thumbnailCacheKey

        assertTrue(file.setLastModified(file.lastModified() + 10_000))

        assertNotEquals(before, repository.favoritesFlow.first()[0].thumbnailCacheKey)
    }

    private fun createTempFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("test content")
        return file
    }
}
