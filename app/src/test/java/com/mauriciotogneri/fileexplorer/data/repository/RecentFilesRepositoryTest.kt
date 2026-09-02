package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.source.FakeRecentFilesSource
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
    fun `recentFilesFlow deduplicates entries sharing a path`() = runTest {
        // Heals stores already corrupted by the pre-fix updatePath: two entries at the same
        // existing path must surface as one, or the path-keyed home LazyRow crashes on duplicate
        // keys.
        val file = createTempFile("dup.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(file.absolutePath, "dup.txt", "text/plain", 2000L),
                RecentFile(file.absolutePath, "dup.txt", "text/plain", 1000L)
            )
        )
        val repository = RecentFilesRepository(source)

        val result = repository.recentFilesFlow.first()

        assertEquals(1, result.size)
        assertEquals(file.absolutePath, result[0].path)
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
    fun `updatePath collapses a rename that collides with an existing recent entry`() = runTest {
        // A stale entry already sits at newPath; renaming a newer entry onto it must not leave two
        // entries sharing a path, which would crash the path-keyed home LazyRow. Entries are ordered
        // most-recent-first (the store's invariant), so distinctBy keeps the freshly-renamed one.
        val oldPath = "/x/draft.docx"
        val newPath = "/x/report.docx"
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(oldPath, "draft.docx", "text/plain", 2000L),
                RecentFile(newPath, "report.docx", "text/plain", 1000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.updatePath(oldPath, newPath)

        val saved = source.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(newPath, saved[0].path)
        assertEquals(2000L, saved[0].lastOpenedTimestamp)
    }

    @Test
    fun `pruneNonExistentFiles removes entries whose files are missing`() = runTest {
        val existingFile = createTempFile("existing.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(File(tempDir, "path.txt").absolutePath, "path.txt", "text/plain", 1000L),
                RecentFile(existingFile.absolutePath, "existing.txt", "text/plain", 2000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

        val saved = source.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(existingFile.absolutePath, saved[0].path)
        assertEquals(1, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles keeps entries whose volume is not mounted`() = runTest {
        // An ejected SD card answers "does not exist" for every path on it at once, and this write
        // is permanent, so a volume this app cannot see is treated as "cannot say", not as "gone".
        val existingFile = createTempFile("existing.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile("/storage/1234-5678/photo.jpg", "photo.jpg", "image/jpeg", 1000L),
                RecentFile(existingFile.absolutePath, "existing.txt", "text/plain", 2000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

        val saved = source.getRecentFiles()
        assertEquals(2, saved.size)
        assertEquals("/storage/1234-5678/photo.jpg", saved[0].path)
        assertEquals(0, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles forgets a missing file once its volume is mounted again`() = runTest {
        // The other half of the rule: the entry survives only while its volume is away.
        val source = FakeRecentFilesSource(
            listOf(RecentFile("/storage/1234-5678/photo.jpg", "photo.jpg", "image/jpeg", 1000L))
        )
        val repository = RecentFilesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath, "/storage/1234-5678"))

        assertEquals(0, source.getRecentFiles().size)
        assertEquals(1, source.updateCount)
    }

    @Test
    fun `pruneNonExistentFiles heals a store holding entries that share a path`() = runTest {
        // Duplicates left by the pre-fix updatePath survive on disk until a write rewrites the
        // store; unhealed they keep consuming MAX_RECENT_FILES slots. Entries are ordered
        // most-recent-first, so the surviving one is the freshest.
        val file = createTempFile("dup.txt")
        val source = FakeRecentFilesSource(
            listOf(
                RecentFile(file.absolutePath, "dup.txt", "text/plain", 2000L),
                RecentFile(file.absolutePath, "dup.txt", "text/plain", 1000L)
            )
        )
        val repository = RecentFilesRepository(source)

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

        val saved = source.getRecentFiles()
        assertEquals(1, saved.size)
        assertEquals(file.absolutePath, saved[0].path)
        assertEquals(2000L, saved[0].lastOpenedTimestamp)
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

        repository.pruneNonExistentFiles(listOf(tempDir.absolutePath))

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

    // The store persists no modification time — reads stamp it from disk so the home screen can key
    // a recent file's thumbnail exactly as the folder list keys the same file's.
    @Test
    fun `recentFilesFlow stamps the file's modification time`() = runTest {
        val file = createTempFile("photo.jpg")
        val repository = RecentFilesRepository(
            FakeRecentFilesSource(
                listOf(RecentFile(file.absolutePath, "photo.jpg", "image/jpeg", 1000L))
            )
        )

        val result = repository.recentFilesFlow.first()

        assertNotEquals(0L, result[0].lastModified)
        assertEquals(file.lastModified(), result[0].lastModified)
    }

    @Test
    fun `getRecentFiles stamps the file's modification time`() = runTest {
        val file = createTempFile("photo.jpg")
        val repository = RecentFilesRepository(
            FakeRecentFilesSource(
                listOf(RecentFile(file.absolutePath, "photo.jpg", "image/jpeg", 1000L))
            )
        )

        val result = repository.getRecentFiles()

        assertNotEquals(0L, result[0].lastModified)
        assertEquals(file.lastModified(), result[0].lastModified)
    }

    @Test
    fun `recentFilesFlow re-keys a recent file that was edited`() = runTest {
        val file = createTempFile("photo.jpg")
        val repository = RecentFilesRepository(
            FakeRecentFilesSource(
                listOf(RecentFile(file.absolutePath, "photo.jpg", "image/jpeg", 1000L))
            )
        )
        val before = repository.recentFilesFlow.first()[0].thumbnailCacheKey

        assertTrue(file.setLastModified(file.lastModified() + 10_000))

        assertNotEquals(before, repository.recentFilesFlow.first()[0].thumbnailCacheKey)
    }

    private fun createTempFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("test content")
        return file
    }
}
