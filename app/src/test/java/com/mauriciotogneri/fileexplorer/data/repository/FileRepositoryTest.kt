package com.mauriciotogneri.fileexplorer.data.repository

import android.os.StatFs
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome
import com.mauriciotogneri.fileexplorer.data.util.isStorageUnavailable
import com.mauriciotogneri.fileexplorer.data.util.isNoSpaceLeft
import com.mauriciotogneri.fileexplorer.data.util.storageAnswersAt
import com.mauriciotogneri.fileexplorer.data.util.thumbnailDiskCacheKeyFor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipException
import java.util.zip.ZipFile

@OptIn(ExperimentalCoilApi::class)
class FileRepositoryTest {

    private val repository = FileRepository(removeFile = ::deleteOnJvm)
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "file_repo_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    // === Mutation notifications ===
    //
    // The home screen caches each location's total size behind a TTL, and this callback is the only
    // thing that invalidates it early. An operation that changes the tree without notifying leaves
    // a card reporting a size that no longer matches disk until the TTL lapses.

    @Test
    fun `createFolder notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }

        repository.createFolder(tempDir.absolutePath, "child")

        assertEquals(1, notifications)
    }

    @Test
    fun `rename notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }
        val file = File(tempDir, "before.txt").apply { writeText("x") }

        repository.rename(fileItemFor(file), "after.txt")

        assertEquals(1, notifications)
    }

    @Test
    fun `delete notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }
        val file = File(tempDir, "gone.txt").apply { writeText("x") }

        repository.delete(listOf(fileItemFor(file)))

        assertEquals(1, notifications)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteWithProgress notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }
        val file = File(tempDir, "gone.txt").apply { writeText("x") }

        repository.deleteWithProgress(listOf(fileItemFor(file))).toList()

        assertEquals(1, notifications)
    }

    @Test
    fun `copyFiles notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }
        val file = File(tempDir, "source.txt").apply { writeText("x") }
        val target = File(tempDir, "target").apply { mkdirs() }

        repository.copyFiles(
            sources = listOf(fileItemFor(file)),
            targetDir = target.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(1, notifications)
    }

    @Test
    fun `copyFiles to a disallowed root does not notify`() = runTest {
        // The allowed-roots check runs first, so a rejected operation never touches disk and must
        // not throw away a still-correct cached size.
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }
        val file = File(tempDir, "source.txt").apply { writeText("x") }

        runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(file)),
                targetDir = File(tempDir, "target").absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(File(tempDir, "elsewhere").absolutePath)
            ).toList()
        }

        assertEquals(0, notifications)
    }

    // Ordering, not just arrival. The hook fires once the tree has stopped changing: invalidating
    // before the work starts does not stay invalidated, because a home-screen pass already
    // measuring writes its pre-mutation sizes back afterwards, where they read as fresh for the
    // whole TTL.

    @Test
    fun `delete notifies only once the files are gone`() = runTest {
        val file = File(tempDir, "gone.txt").apply { writeText("x") }
        var existedWhenNotified: Boolean? = null
        val repository = FileRepository(removeFile = ::deleteOnJvm) { existedWhenNotified = file.exists() }

        repository.delete(listOf(fileItemFor(file)))

        assertEquals(false, existedWhenNotified)
    }

    @Test
    fun `createFolder notifies only once the folder exists`() = runTest {
        val child = File(tempDir, "child")
        var existedWhenNotified: Boolean? = null
        val repository = FileRepository(removeFile = ::deleteOnJvm) { existedWhenNotified = child.exists() }

        repository.createFolder(tempDir.absolutePath, "child")

        assertEquals(true, existedWhenNotified)
    }

    @Test
    fun `createFolder does not notify when the name is rejected`() = runTest {
        // Validation runs before anything reaches disk, so a still-correct cached size survives.
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }

        assertFalse(repository.createFolder(tempDir.absolutePath, "bad/name"))

        assertEquals(0, notifications)
    }

    @Test
    fun `rename notifies only once the file has moved`() = runTest {
        val file = File(tempDir, "before.txt").apply { writeText("x") }
        val renamed = File(tempDir, "after.txt")
        var movedWhenNotified: Boolean? = null
        val repository = FileRepository(removeFile = ::deleteOnJvm) { movedWhenNotified = renamed.exists() && !file.exists() }

        repository.rename(fileItemFor(file), "after.txt")

        assertEquals(true, movedWhenNotified)
    }

    @Test
    fun `deleteWithProgress notifies only once the files are gone`() = runTest {
        // Stated against the tree rather than against emission order: flowOn buffers between the
        // producer that runs the hook and the collector, so which of the two appends to a shared
        // list first is not something this flow guarantees.
        val file = File(tempDir, "gone.txt").apply { writeText("x") }
        var existedWhenNotified: Boolean? = null
        val repository = FileRepository(removeFile = ::deleteOnJvm) { existedWhenNotified = file.exists() }

        repository.deleteWithProgress(listOf(fileItemFor(file))).toList()

        assertEquals(false, existedWhenNotified)
    }

    @Test
    fun `deleteWithProgress notifies when collection stops before the tree is fully deleted`() = runTest {
        // A half-deleted tree matches the cached size even less than a fully deleted one, so an
        // abandoned operation has to invalidate too.
        val files = (1..5).map { index -> File(tempDir, "f$index.txt").apply { writeText("x") } }
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }

        repository.deleteWithProgress(files.map { fileItemFor(it) }).first()

        assertEquals(1, notifications)
    }

    @Test
    fun `reading does not notify`() = runTest {
        var notifications = 0
        val repository = FileRepository(removeFile = ::deleteOnJvm) { notifications++ }
        val file = File(tempDir, "a.txt").apply { writeText("x") }

        repository.totalNodeCount(listOf(fileItemFor(file)))
        repository.totalSize(listOf(fileItemFor(file)))

        assertEquals(0, notifications)
    }

    private fun fileItemFor(file: File) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = file.isDirectory,
        size = file.length(),
        lastModified = file.lastModified(),
        createdTime = file.lastModified(),
        mimeType = "text/plain"
    )

    // === Thumbnail eviction ===
    //
    // Unlike the memory cache, an extracted thumbnail is a file that outlives the process, so
    // nothing drops a deleted file's copy until the cache fills up and eviction reclaims it.

    @Test
    fun `delete drops the thumbnail cached for the file`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val key = requireNotNull(thumbnailDiskCacheKeyFor(video))

        repository.delete(listOf(fileItemFor(video)))

        verify { diskCache.remove(key) }
    }

    @Test
    fun `deleteWithProgress drops the thumbnail cached for the file`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val key = requireNotNull(thumbnailDiskCacheKeyFor(video))

        repository.deleteWithProgress(listOf(fileItemFor(video))).toList()

        verify { diskCache.remove(key) }
    }

    // A renamed file keeps its content but stops answering to the name its thumbnail is keyed by.
    @Test
    fun `rename drops the thumbnail cached under the old name`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val key = requireNotNull(thumbnailDiskCacheKeyFor(video))

        assertNotNull(repository.rename(fileItemFor(video), "renamed.mp4"))

        verify { diskCache.remove(key) }
    }

    // Renaming to a name already taken is refused, so the file keeps both its path and its
    // thumbnail.
    @Test
    fun `a rejected rename keeps the thumbnail`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        File(tempDir, "taken.mp4").apply { writeText("y") }

        assertNull(repository.rename(fileItemFor(video), "taken.mp4"))

        verify(exactly = 0) { diskCache.remove(any()) }
    }

    // A move empties the source path just as a delete does, so the entry keyed to it is as dead.
    @Test
    fun `moving a file drops the thumbnail cached at its old path`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val key = requireNotNull(thumbnailDiskCacheKeyFor(video))
        val target = File(tempDir, "target").apply { mkdirs() }

        repository.copyFiles(
            sources = listOf(fileItemFor(video)),
            targetDir = target.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        verify { diskCache.remove(key) }
    }

    // Copying leaves the source where it is, so its thumbnail is still the right one for that path.
    @Test
    fun `copying a file keeps the thumbnail cached at its path`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val target = File(tempDir, "target").apply { mkdirs() }

        repository.copyFiles(
            sources = listOf(fileItemFor(video)),
            targetDir = target.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        verify(exactly = 0) { diskCache.remove(any()) }
    }

    // Nearly every file has no extracted thumbnail, and a delete walks every file in the tree, so
    // those must not each cost a cache lookup.
    @Test
    fun `delete does not look up files that have no thumbnail`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache }, removeFile = ::deleteOnJvm)
        val text = File(tempDir, "notes.txt").apply { writeText("x") }

        repository.delete(listOf(fileItemFor(text)))

        verify(exactly = 0) { diskCache.remove(any()) }
    }

    // === Sorting Tests ===

    @Test
    fun `sortFiles places folders before files`() {
        val files = listOf(
            createFileItem(name = "file.txt", isDirectory = false),
            createFileItem(name = "folder", isDirectory = true)
        )

        val sorted = repository.sortFiles(files, SortMode.NAME_ASC)

        assertEquals("folder", sorted[0].name)
        assertEquals("file.txt", sorted[1].name)
    }

    @Test
    fun `sortFiles NAME_ASC sorts alphabetically ascending`() {
        val files = listOf(
            createFileItem(name = "zebra.txt"),
            createFileItem(name = "apple.txt"),
            createFileItem(name = "Banana.txt")
        )

        val sorted = repository.sortFiles(files, SortMode.NAME_ASC)

        assertEquals("apple.txt", sorted[0].name)
        assertEquals("Banana.txt", sorted[1].name)
        assertEquals("zebra.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles NAME_DESC sorts alphabetically descending`() {
        val files = listOf(
            createFileItem(name = "apple.txt"),
            createFileItem(name = "zebra.txt"),
            createFileItem(name = "Banana.txt")
        )

        val sorted = repository.sortFiles(files, SortMode.NAME_DESC)

        assertEquals("zebra.txt", sorted[0].name)
        assertEquals("Banana.txt", sorted[1].name)
        assertEquals("apple.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles SIZE_ASC sorts by size ascending`() {
        val files = listOf(
            createFileItem(name = "large.txt", size = 1000),
            createFileItem(name = "small.txt", size = 100),
            createFileItem(name = "medium.txt", size = 500)
        )

        val sorted = repository.sortFiles(files, SortMode.SIZE_ASC)

        assertEquals("small.txt", sorted[0].name)
        assertEquals("medium.txt", sorted[1].name)
        assertEquals("large.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles SIZE_DESC sorts by size descending`() {
        val files = listOf(
            createFileItem(name = "small.txt", size = 100),
            createFileItem(name = "large.txt", size = 1000),
            createFileItem(name = "medium.txt", size = 500)
        )

        val sorted = repository.sortFiles(files, SortMode.SIZE_DESC)

        assertEquals("large.txt", sorted[0].name)
        assertEquals("medium.txt", sorted[1].name)
        assertEquals("small.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles DATE_ASC sorts by date ascending`() {
        val files = listOf(
            createFileItem(name = "newest.txt", lastModified = 3000),
            createFileItem(name = "oldest.txt", lastModified = 1000),
            createFileItem(name = "middle.txt", lastModified = 2000)
        )

        val sorted = repository.sortFiles(files, SortMode.DATE_ASC)

        assertEquals("oldest.txt", sorted[0].name)
        assertEquals("middle.txt", sorted[1].name)
        assertEquals("newest.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles DATE_DESC sorts by date descending`() {
        val files = listOf(
            createFileItem(name = "oldest.txt", lastModified = 1000),
            createFileItem(name = "newest.txt", lastModified = 3000),
            createFileItem(name = "middle.txt", lastModified = 2000)
        )

        val sorted = repository.sortFiles(files, SortMode.DATE_DESC)

        assertEquals("newest.txt", sorted[0].name)
        assertEquals("middle.txt", sorted[1].name)
        assertEquals("oldest.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles maintains folders first with any sort mode`() {
        val files = listOf(
            createFileItem(name = "z_file.txt", isDirectory = false, size = 100),
            createFileItem(name = "a_folder", isDirectory = true, size = 0),
            createFileItem(name = "b_folder", isDirectory = true, size = 0),
            createFileItem(name = "a_file.txt", isDirectory = false, size = 1000)
        )

        val sortedBySize = repository.sortFiles(files, SortMode.SIZE_DESC)

        assertEquals(true, sortedBySize[0].isDirectory)
        assertEquals(true, sortedBySize[1].isDirectory)
        assertEquals(false, sortedBySize[2].isDirectory)
        assertEquals(false, sortedBySize[3].isDirectory)
        assertEquals("a_file.txt", sortedBySize[2].name)
        assertEquals("z_file.txt", sortedBySize[3].name)
    }

    @Test
    fun `sortFiles handles empty list`() {
        val sorted = repository.sortFiles(emptyList(), SortMode.NAME_ASC)
        assertEquals(emptyList<FileItem>(), sorted)
    }

    @Test
    fun `sortFiles handles single item`() {
        val files = listOf(createFileItem(name = "only.txt"))
        val sorted = repository.sortFiles(files, SortMode.NAME_ASC)
        assertEquals(1, sorted.size)
        assertEquals("only.txt", sorted[0].name)
    }

    @Test
    fun `sortFiles NAME sort is stable for names differing only in case`() {
        // The name sort lowercases keys, so these collide; a stable sort must keep input order.
        val ascending = repository.sortFiles(
            listOf(createFileItem(name = "file.txt"), createFileItem(name = "File.txt")),
            SortMode.NAME_ASC
        )
        assertEquals("file.txt", ascending[0].name)
        assertEquals("File.txt", ascending[1].name)

        val descending = repository.sortFiles(
            listOf(createFileItem(name = "file.txt"), createFileItem(name = "File.txt")),
            SortMode.NAME_DESC
        )
        assertEquals("file.txt", descending[0].name)
        assertEquals("File.txt", descending[1].name)
    }

    // === listFiles Tests ===

    @Test
    fun `listFiles returns empty list for empty directory`() = runTest {
        val emptyDir = File(tempDir, "empty")
        emptyDir.mkdirs()

        val files = repository.listFiles(emptyDir.absolutePath, false, SortMode.NAME_ASC)

        assertTrue(files.isEmpty())
    }

    @Test
    fun `listFiles returns files and folders sorted`() = runTest {
        File(tempDir, "folder").mkdirs()
        File(tempDir, "file.txt").createNewFile()

        val files = repository.listFiles(tempDir.absolutePath, false, SortMode.NAME_ASC)

        assertEquals(2, files.size)
        assertEquals("folder", files[0].name)
        assertTrue(files[0].isDirectory)
        assertEquals("file.txt", files[1].name)
        assertFalse(files[1].isDirectory)
    }

    @Test
    fun `listFiles filters hidden files when showHidden is false`() = runTest {
        File(tempDir, ".hidden").createNewFile()
        File(tempDir, "visible.txt").createNewFile()

        val files = repository.listFiles(tempDir.absolutePath, false, SortMode.NAME_ASC)

        assertEquals(1, files.size)
        assertEquals("visible.txt", files[0].name)
    }

    @Test
    fun `listFiles includes hidden files when showHidden is true`() = runTest {
        File(tempDir, ".hidden.txt").createNewFile()
        File(tempDir, "visible.txt").createNewFile()

        val files = repository.listFiles(tempDir.absolutePath, true, SortMode.NAME_ASC)

        assertEquals(2, files.size)
        assertTrue(files.any { it.name == ".hidden.txt" })
        assertTrue(files.any { it.name == "visible.txt" })
    }

    @Test
    fun `listFiles returns empty list for non-existent path`() = runTest {
        val nonExistent = File(tempDir, "does_not_exist")

        val files = repository.listFiles(nonExistent.absolutePath, false, SortMode.NAME_ASC)

        assertTrue(files.isEmpty())
    }

    @Test
    fun `listFiles returns empty list for file path instead of directory`() = runTest {
        val file = File(tempDir, "file.txt")
        file.createNewFile()

        val files = repository.listFiles(file.absolutePath, false, SortMode.NAME_ASC)

        assertTrue(files.isEmpty())
    }

    // === countChildren Tests ===

    @Test
    fun `countChildren returns number of direct children`() = runTest {
        val dir = File(tempDir, "dir")
        dir.mkdirs()
        File(dir, "a.txt").createNewFile()
        File(dir, "b.txt").createNewFile()
        File(dir, "sub").mkdirs()

        assertEquals(3, repository.countChildren(dir.absolutePath, showHidden = false))
    }

    @Test
    fun `countChildren counts hidden entries when showHidden is true`() = runTest {
        val dir = File(tempDir, "dir")
        dir.mkdirs()
        File(dir, "visible.txt").createNewFile()
        File(dir, ".hidden").createNewFile()

        assertEquals(2, repository.countChildren(dir.absolutePath, showHidden = true))
    }

    @Test
    fun `countChildren excludes hidden entries when showHidden is false`() = runTest {
        val dir = File(tempDir, "dir")
        dir.mkdirs()
        File(dir, "visible.txt").createNewFile()
        File(dir, ".hidden").createNewFile()
        File(dir, ".hiddenDir").mkdirs()

        assertEquals(1, repository.countChildren(dir.absolutePath, showHidden = false))
    }

    @Test
    fun `countChildren matches the number of rows listFiles returns`() = runTest {
        val dir = File(tempDir, "dir")
        dir.mkdirs()
        File(dir, "visible.txt").createNewFile()
        File(dir, "sub").mkdirs()
        File(dir, ".hidden").createNewFile()

        for (showHidden in listOf(false, true)) {
            val listed = repository.listFiles(dir.absolutePath, showHidden, SortMode.NAME_ASC)

            assertEquals(listed.size, repository.countChildren(dir.absolutePath, showHidden))
        }
    }

    @Test
    fun `countChildren returns zero for empty directory`() = runTest {
        val dir = File(tempDir, "empty")
        dir.mkdirs()

        assertEquals(0, repository.countChildren(dir.absolutePath, showHidden = false))
    }

    @Test
    fun `countChildren returns null for non-existent path`() = runTest {
        val nonExistent = File(tempDir, "missing")

        assertNull(repository.countChildren(nonExistent.absolutePath, showHidden = false))
    }

    // === createFolder Tests ===

    @Test
    fun `createFolder creates new folder successfully`() = runTest {
        val result = repository.createFolder(tempDir.absolutePath, "NewFolder")

        assertTrue(result)
        assertTrue(File(tempDir, "NewFolder").exists())
        assertTrue(File(tempDir, "NewFolder").isDirectory)
    }

    @Test
    fun `createFolder returns false for existing folder name`() = runTest {
        File(tempDir, "Existing").mkdirs()

        val result = repository.createFolder(tempDir.absolutePath, "Existing")

        assertFalse(result)
    }

    @Test
    fun `createFolder returns false for invalid characters in name`() = runTest {
        val result = repository.createFolder(tempDir.absolutePath, "invalid/name")

        assertFalse(result)
    }

    @Test
    fun `createFolder rejects path traversal attempt`() = runTest {
        val result = repository.createFolder(tempDir.absolutePath, "../escape")

        assertFalse(result)
    }

    @Test
    fun `createFolder rejects backslash path traversal`() = runTest {
        val result = repository.createFolder(tempDir.absolutePath, "..\\escape")

        assertFalse(result)
    }

    // === rename Tests ===

    @Test
    fun `rename renames file successfully`() = runTest {
        val file = File(tempDir, "original.txt")
        file.writeText("content")
        val fileItem = createFileItem(path = file.absolutePath, name = "original.txt")

        val result = repository.rename(fileItem, "renamed.txt")

        assertNotNull(result)
        assertEquals(file.absolutePath, result?.oldPath)
        assertTrue(File(tempDir, "renamed.txt").exists())
        assertFalse(File(tempDir, "original.txt").exists())
    }

    @Test
    fun `rename handles case-only rename`() = runTest {
        val file = File(tempDir, "lowercase.txt")
        file.writeText("content")
        val fileItem = createFileItem(path = file.absolutePath, name = "lowercase.txt")

        val result = repository.rename(fileItem, "LOWERCASE.txt")

        assertNotNull(result)
        assertTrue(result?.isCaseOnlyRename == true)
    }

    @Test
    fun `rename returns null for existing target name`() = runTest {
        val file1 = File(tempDir, "file1.txt")
        val file2 = File(tempDir, "file2.txt")
        file1.writeText("content1")
        file2.writeText("content2")
        val fileItem = createFileItem(path = file1.absolutePath, name = "file1.txt")

        val result = repository.rename(fileItem, "file2.txt")

        assertNull(result)
        assertTrue(file1.exists())
        assertTrue(file2.exists())
    }

    @Test
    fun `rename returns null for invalid characters`() = runTest {
        val file = File(tempDir, "original.txt")
        file.writeText("content")
        val fileItem = createFileItem(path = file.absolutePath, name = "original.txt")

        val result = repository.rename(fileItem, "invalid/name.txt")

        assertNull(result)
        assertTrue(file.exists())
    }

    @Test
    fun `rename returns null for path traversal attempt`() = runTest {
        val file = File(tempDir, "original.txt")
        file.writeText("content")
        val fileItem = createFileItem(path = file.absolutePath, name = "original.txt")

        val result = repository.rename(fileItem, "../escape.txt")

        assertNull(result)
    }

    // === delete Tests ===

    // Stand-ins for OsConstants, whose every field reads 0 off device — see deleteFailureFor.
    // The values are the Linux ones and only have to be distinct from each other here.
    private val EACCES = 13
    private val EROFS = 30

    /**
     * A `removeFile` for the JVM, where [android.system.Os] is a stub that throws.
     *
     * Answers the same three states the production one does, which is what keeps the delete tests
     * statements about the repository instead of about this stand-in. `File.delete()` reports an
     * already-absent path as a failure, so the second `exists()` is what recovers the distinction
     * `removePath` reads from ENOENT; that the platform really does raise ENOENT there is
     * `FileAccessTest`'s to assert, on a device.
     *
     * [ERRNO_UNKNOWN] for a real failure, since `File.delete()` has no reason to give.
     */
    private fun deleteOnJvm(file: File): RemoveOutcome = when {
        file.delete() -> RemoveOutcome.Removed
        !file.exists() -> RemoveOutcome.AlreadyAbsent
        else -> RemoveOutcome.Failed(ERRNO_UNKNOWN)
    }


    @Test
    fun `delete removes file successfully`() = runTest {
        val file = File(tempDir, "toDelete.txt")
        file.writeText("content")
        val fileItem = createFileItem(path = file.absolutePath, name = "toDelete.txt")

        val result = repository.delete(listOf(fileItem))

        assertTrue(result.success)
        assertFalse(file.exists())
    }

    @Test
    fun `delete removes folder with contents recursively`() = runTest {
        val folder = File(tempDir, "folderToDelete")
        folder.mkdirs()
        File(folder, "child1.txt").writeText("content1")
        File(folder, "child2.txt").writeText("content2")
        File(folder, "subFolder").mkdirs()
        File(folder, "subFolder/nested.txt").writeText("nested")
        val fileItem = createFileItem(
            path = folder.absolutePath,
            name = "folderToDelete",
            isDirectory = true
        )

        val result = repository.delete(listOf(fileItem))

        assertTrue(result.success)
        assertFalse(folder.exists())
    }

    // A delete is asked for a path that holds nothing afterwards, and one that already held nothing
    // satisfies that. Reporting it as a failure put an error toast in front of a user whose file
    // another app had already removed — the stale search result, the stale recents entry. How much
    // of the field's `unknown` volume that accounted for is not something the old event could say:
    // it recorded neither a cause nor a source. What it did record is that `unknown` was the only
    // label this path could emit, so it covered every delete failure whatever caused it.
    @Test
    fun `delete treats an already absent path as done`() = runTest {
        val fileItem = createFileItem(
            path = File(tempDir, "nonexistent.txt").absolutePath,
            name = "nonexistent.txt"
        )

        val result = repository.delete(listOf(fileItem))

        assertTrue(result.success)
        assertNull(result.failureErrno)
    }

    @Test
    fun `delete multiple files succeeds only if all succeed`() = runTest {
        val file1 = File(tempDir, "file1.txt")
        val file2 = File(tempDir, "file2.txt")
        file1.writeText("content1")
        file2.writeText("content2")
        val items = listOf(
            createFileItem(path = file1.absolutePath, name = "file1.txt"),
            createFileItem(path = file2.absolutePath, name = "file2.txt")
        )

        val result = repository.delete(items)

        assertTrue(result.success)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    // `files.all { ... }` stops at the first false, so a multi-selection whose first item could
    // not be deleted used to leave every later one on disk behind a message that named none of
    // them. The second file is the assertion that matters; the first only has to fail.
    @Test
    fun `delete attempts every item after one fails`() = runTest {
        val undeletable = File(tempDir, "undeletable.txt").apply { writeText("stays") }
        val deletable = File(tempDir, "deletable.txt").apply { writeText("goes") }
        val repository = FileRepository(
            removeFile = { file ->
                if (file.absolutePath == undeletable.absolutePath) {
                    RemoveOutcome.Failed(EACCES)
                } else {
                    deleteOnJvm(file)
                }
            }
        )
        val items = listOf(
            createFileItem(path = undeletable.absolutePath, name = "undeletable.txt"),
            createFileItem(path = deletable.absolutePath, name = "deletable.txt")
        )

        val result = repository.delete(items)

        assertFalse(result.success)
        assertEquals(EACCES, result.failureErrno)
        assertTrue(undeletable.exists())
        assertFalse("The item after the failure must still be attempted", deletable.exists())
    }

    // The caller routes these two apart — one to MediaStore's row delete, the other to a scan — so
    // the repository has to tell them apart in the first place. A path nothing was ever at is not
    // a path this app emptied.
    @Test
    fun `delete separates roots it removed from roots that were already gone`() = runTest {
        val present = File(tempDir, "present.txt").apply { writeText("goes") }
        val absent = File(tempDir, "absent.txt")
        val items = listOf(
            createFileItem(path = present.absolutePath, name = "present.txt"),
            createFileItem(path = absent.absolutePath, name = "absent.txt")
        )

        val result = repository.delete(items)

        assertTrue(result.success)
        assertEquals(listOf(present.absolutePath), result.removedPaths)
        assertEquals(listOf(absent.absolutePath), result.alreadyAbsentPaths)
        assertEquals(2, result.clearedCount)
    }

    // A directory whose children were removed by something else, and which this app then removed
    // itself, is a root this app emptied — the removal of the directory is the removal. The walk
    // has to answer on the whole subtree rather than on the last node it touched.
    @Test
    fun `delete counts a directory it removed as removed even when its children were gone`() = runTest {
        val folder = File(tempDir, "folder").apply { mkdirs() }
        val item = createFileItem(path = folder.absolutePath, name = "folder", isDirectory = true)

        val result = repository.delete(listOf(item))

        assertEquals(listOf(folder.absolutePath), result.removedPaths)
        assertTrue(result.alreadyAbsentPaths.isEmpty())
    }

    // The errno reported is the first one the walk met, depth-first, because that is the one that
    // names the cause: a directory whose child survived fails with ENOTEMPTY afterwards, which
    // only restates that the child survived.
    @Test
    fun `delete reports the child errno rather than the directory's`() = runTest {
        val folder = File(tempDir, "folder").apply { mkdirs() }
        val child = File(folder, "child.txt").apply { writeText("stays") }
        val repository = FileRepository(
            removeFile = { file ->
                if (file.absolutePath == child.absolutePath) {
                    RemoveOutcome.Failed(EROFS)
                } else {
                    deleteOnJvm(file)
                }
            }
        )
        val fileItem = createFileItem(path = folder.absolutePath, name = "folder", isDirectory = true)

        val result = repository.delete(listOf(fileItem))

        assertEquals(EROFS, result.failureErrno)
        assertTrue("The directory is still attempted after a child fails", folder.exists())
    }

    // A move source something else removed while the copy ran satisfies the move — the path holds
    // nothing and the copy is made — but this app did not remove it and cannot say what occupies
    // the path now. `deletedSourcePaths` is handed to MediaStore as paths whose files are gone, and
    // a media provider unlinks the file behind a row it drops, so an already-absent source that
    // entered that batch would delete whatever took the path over.
    @Test
    fun `move keeps an already absent source out of the provider delete batch`() = runTest {
        val source = File(tempDir, "moved.txt").apply { writeText("content") }
        val target = File(tempDir, "target").apply { mkdirs() }
        val repository = FileRepository(
            removeFile = { file ->
                if (file.absolutePath == source.absolutePath) {
                    RemoveOutcome.AlreadyAbsent
                } else {
                    deleteOnJvm(file)
                }
            }
        )

        val progress = repository.copyFiles(
            sources = listOf(fileItemFor(source)),
            targetDir = target.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList().last()

        assertTrue("The copy must still be made", File(target, "moved.txt").exists())
        assertFalse(
            "An already absent source must never be reported as one this app deleted",
            source.absolutePath in progress.deletedSourcePaths
        )
        assertTrue(
            "It is reported for scanning instead",
            source.absolutePath in progress.absentSourcePaths
        )
        assertFalse("The move must not be reported as failed", progress.sourceDeleteFailed)
    }

    // The other half of the same split: a source this app really did unlink is safe to report, and
    // must still be reported — otherwise every moved file keeps a MediaStore row pointing at a path
    // it has left.
    @Test
    fun `move reports a source it removed itself`() = runTest {
        val source = File(tempDir, "moved.txt").apply { writeText("content") }
        val target = File(tempDir, "target").apply { mkdirs() }

        val progress = repository.copyFiles(
            sources = listOf(fileItemFor(source)),
            targetDir = target.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList().last()

        assertTrue(source.absolutePath in progress.deletedSourcePaths)
        assertTrue(progress.absentSourcePaths.isEmpty())
    }

    // === deleteWithProgress Tests ===

    @Test
    fun `deleteWithProgress reports progress correctly`() = runTest {
        val folder = File(tempDir, "progressFolder")
        folder.mkdirs()
        File(folder, "file1.txt").writeText("content1")
        File(folder, "file2.txt").writeText("content2")
        val fileItem = createFileItem(
            path = folder.absolutePath,
            name = "progressFolder",
            isDirectory = true
        )

        val progressList = repository.deleteWithProgress(listOf(fileItem)).toList()

        assertTrue(progressList.isNotEmpty())
        val finalProgress = progressList.last()
        assertTrue(finalProgress.isComplete)
        assertEquals(0, finalProgress.failedFiles)
        // The folder holds 2 leaf files; the directory itself must not inflate the totals.
        assertEquals(2, finalProgress.totalFiles)
        assertEquals(finalProgress.totalFiles, finalProgress.deletedFiles)
    }

    @Test
    fun `deleteWithProgress counts only leaf files not directories`() = runTest {
        val root = File(tempDir, "root")
        val sub = File(root, "sub")
        sub.mkdirs()
        File(root, "a.txt").writeText("a")
        File(sub, "b.txt").writeText("b")
        File(sub, "c.txt").writeText("c")
        val fileItem = createFileItem(
            path = root.absolutePath,
            name = "root",
            isDirectory = true
        )

        val finalProgress = repository.deleteWithProgress(listOf(fileItem)).toList().last()

        assertTrue(finalProgress.isComplete)
        // 3 leaf files only — the `root` and `sub` directories are deleted but not counted.
        assertEquals(3, finalProgress.totalFiles)
        assertEquals(3, finalProgress.deletedFiles)
        assertEquals(0, finalProgress.failedFiles)
        assertFalse(root.exists())
    }

    // A leaf something else unlinked between the walk's listing and its own attempt is not a
    // failure — the path holds nothing, which is what was asked — and it has to keep counting
    // toward [DeleteProgress.deletedFiles] or the dialog's fraction stalls short of full over a
    // tree being emptied underneath it. That is the whole reason already-absent leaves are folded
    // into `deletedFiles` rather than split off into a tally of their own.
    @Test
    fun `deleteWithProgress counts a leaf something else removed toward the fraction`() = runTest {
        val root = File(tempDir, "root")
        root.mkdirs()
        File(root, "present.txt").writeText("data")
        val gone = File(root, "gone.txt").apply { writeText("data") }
        val repository = FileRepository(
            removeFile = { file ->
                if (file.absolutePath == gone.absolutePath) {
                    // The race as the walk really meets it: the path is empty by the time the
                    // unlink lands, so `removePath` answers ENOENT rather than succeeding.
                    file.delete()
                    RemoveOutcome.AlreadyAbsent
                } else {
                    deleteOnJvm(file)
                }
            }
        )
        val fileItem = createFileItem(
            path = root.absolutePath,
            name = "root",
            isDirectory = true
        )

        val finalProgress = repository.deleteWithProgress(listOf(fileItem)).toList().last()

        assertTrue(finalProgress.isComplete)
        assertEquals(2, finalProgress.totalFiles)
        // Both leaves count, so the fraction reaches full instead of stopping at one half.
        assertEquals(finalProgress.totalFiles, finalProgress.deletedFiles)
        assertEquals(0, finalProgress.failedFiles)
        assertFalse(finalProgress.structuralDeleteFailed)
        // The root is still one this app emptied — it unlinked `present.txt` and the directory
        // itself — so the prefix-matching row delete stays safe on it.
        assertEquals(listOf(root.absolutePath), finalProgress.removedRootPaths)
        assertTrue(finalProgress.absentRootPaths.isEmpty())
    }

    @Test
    fun `deleteWithProgress deletes a symlink without following or counting it`() = runTest {
        val external = File(tempDir, "external.txt")
        external.writeText("keep me")
        val root = File(tempDir, "root")
        root.mkdirs()
        File(root, "real.txt").writeText("data")
        val link = File(root, "link")
        val created = try {
            Files.createSymbolicLink(link.toPath(), external.toPath())
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue(
            "Filesystem does not support symbolic links",
            created && Files.isSymbolicLink(link.toPath())
        )
        val fileItem = createFileItem(
            path = root.absolutePath,
            name = "root",
            isDirectory = true
        )

        val finalProgress = repository.deleteWithProgress(listOf(fileItem)).toList().last()

        assertTrue(finalProgress.isComplete)
        // Only `real.txt` counts; the symlink (like the directory) is excluded from the totals.
        assertEquals(1, finalProgress.totalFiles)
        assertEquals(1, finalProgress.deletedFiles)
        assertEquals(0, finalProgress.failedFiles)
        assertFalse(root.exists()) // symlink and directory removed
        assertTrue(external.exists()) // symlink was not followed
    }

    @Test
    fun `deleteWithProgress flags structuralDeleteFailed when a directory cannot be removed`() = runTest {
        val parent = File(tempDir, "parent")
        val target = File(parent, "target")
        target.mkdirs()
        File(target, "file.txt").writeText("data")
        val fileItem = createFileItem(
            path = target.absolutePath,
            name = "target",
            isDirectory = true
        )

        // Make the parent non-writable so `target` itself cannot be unlinked, while its child file
        // (gated by `target`'s own still-writable bit) deletes successfully. Stands in for a
        // read-only-mounted volume. Skipped when the filesystem does not enforce the permission.
        parent.setWritable(false, false)
        try {
            assumeTrue("Filesystem does not enforce directory write permission", !parent.canWrite())

            val finalProgress = repository.deleteWithProgress(listOf(fileItem)).toList().last()

            assertTrue(finalProgress.isComplete)
            assertTrue(finalProgress.structuralDeleteFailed)
            // The leaf file deleted and is counted; the undeletable directory is not.
            assertEquals(1, finalProgress.totalFiles)
            assertEquals(1, finalProgress.deletedFiles)
            assertEquals(0, finalProgress.failedFiles)
            assertFalse(File(target, "file.txt").exists())
            assertTrue(target.exists()) // directory could not be removed
        } finally {
            parent.setWritable(true, false)
        }
    }

    // A structural failure has to be attributed to the root that caused it, not to the operation.
    // With an operation-wide flag, the second root's own failure is invisible — the flag was
    // already set — so a root still sitting on disk would be reported as emptied and handed to the
    // prefix-matching MediaStore row delete.
    @Test
    fun `deleteWithProgress excludes a later root that also fails structurally`() = runTest {
        val rootA = File(tempDir, "rootA")
        val rootB = File(tempDir, "rootB")
        rootA.mkdirs()
        rootB.mkdirs()
        File(rootA, "a.txt").writeText("data")
        File(rootB, "b.txt").writeText("data")
        val repository = FileRepository(
            removeFile = { file ->
                // Both root directories refuse to go, their children unlink cleanly — the
                // ENOTEMPTY/EBUSY shape, without needing a racing writer or a mount point.
                if (file.absolutePath == rootA.absolutePath ||
                    file.absolutePath == rootB.absolutePath
                ) {
                    RemoveOutcome.Failed(ERRNO_UNKNOWN)
                } else {
                    deleteOnJvm(file)
                }
            }
        )
        val items = listOf(rootA, rootB).map { root ->
            createFileItem(path = root.absolutePath, name = root.name, isDirectory = true)
        }

        val finalProgress = repository.deleteWithProgress(items).toList().last()

        assertTrue(finalProgress.isComplete)
        assertTrue(finalProgress.structuralDeleteFailed)
        // Neither root was emptied, so neither may reach MediaStore by either route.
        assertTrue(finalProgress.removedRootPaths.isEmpty())
        assertTrue(finalProgress.absentRootPaths.isEmpty())
        assertTrue(rootA.exists())
        assertTrue(rootB.exists())
    }

    // === copyFiles Tests ===

    @Test
    fun `copyFiles copies file with correct content`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("Hello World")
        val sourceItem = createFileItem(path = sourceFile.absolutePath, name = "test.txt")

        val progressList = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertTrue(progressList.isNotEmpty())
        val finalProgress = progressList.last()
        assertTrue(finalProgress.isComplete)
        assertTrue(File(targetDir, "test.txt").exists())
        assertEquals("Hello World", File(targetDir, "test.txt").readText())
        assertTrue(sourceFile.exists())
    }

    @Test
    fun `copyFiles handles name collision with incrementing suffix`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("source content")
        File(targetDir, "test.txt").writeText("existing content")
        val sourceItem = createFileItem(path = sourceFile.absolutePath, name = "test.txt")

        val progressList = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertTrue(progressList.last().isComplete)
        assertTrue(File(targetDir, "test.txt").exists())
        assertTrue(File(targetDir, "test (1).txt").exists())
        assertEquals("existing content", File(targetDir, "test.txt").readText())
        assertEquals("source content", File(targetDir, "test (1).txt").readText())
    }

    @Test
    fun `copyFiles numbers a name with no extension without adding a dot`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        File(sourceDir, "README").writeText("source content")
        File(targetDir, "README").writeText("existing content")
        val sourceItem = createFileItem(path = File(sourceDir, "README").absolutePath, name = "README")

        repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        // There is no extension to put back, so nothing may be appended after the number.
        assertTrue(File(targetDir, "README (1)").exists())
        assertEquals("source content", File(targetDir, "README (1)").readText())
    }

    @Test
    fun `copyFiles numbers a dotfile after its name, not inside it`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        File(sourceDir, ".gitignore").writeText("source content")
        File(targetDir, ".gitignore").writeText("existing content")
        val sourceItem = createFileItem(path = File(sourceDir, ".gitignore").absolutePath, name = ".gitignore")

        repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        // The leading dot is part of the name, not an extension separator: reading it as one leaves
        // the copy with no name at all.
        assertTrue(File(targetDir, ".gitignore (1)").exists())
        assertEquals("source content", File(targetDir, ".gitignore (1)").readText())
    }

    @Test
    fun `copyFiles keeps a trailing dot in the name it numbers`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        File(sourceDir, "notes.").writeText("source content")
        File(targetDir, "notes.").writeText("existing content")
        val sourceItem = createFileItem(path = File(sourceDir, "notes.").absolutePath, name = "notes.")

        repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        // A trailing dot has nothing after it to be an extension, so it stays where it is and the
        // number goes after the whole name — not "notes (1).".
        assertTrue(File(targetDir, "notes. (1)").exists())
        assertEquals("source content", File(targetDir, "notes. (1)").readText())
    }

    @Test
    fun `copyFiles preserves the modification time of the source`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("content")
        val modifiedTime = 1_600_000_000_000L
        Files.setLastModifiedTime(sourceFile.toPath(), FileTime.fromMillis(modifiedTime))
        assumeTrue(
            "Filesystem does not preserve millisecond timestamps",
            sourceFile.lastModified() == modifiedTime
        )
        val sourceItem = createFileItem(path = sourceFile.absolutePath, name = "test.txt")

        repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(modifiedTime, File(targetDir, "test.txt").lastModified())
    }

    @Test
    fun `copyFiles copies items with a pre-epoch modification time`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        val sourceFolder = File(sourceDir, "folder")
        sourceFolder.mkdirs()
        val sourceFile = File(sourceFolder, "test.txt")
        sourceFile.writeText("content")
        Files.setLastModifiedTime(sourceFile.toPath(), FileTime.fromMillis(-1000L))
        Files.setLastModifiedTime(sourceFolder.toPath(), FileTime.fromMillis(-2000L))
        assumeTrue(
            "Filesystem does not support pre-epoch timestamps",
            sourceFile.lastModified() < 0 && sourceFolder.lastModified() < 0
        )
        val sourceItem = createFileItem(
            path = sourceFolder.absolutePath,
            name = "folder",
            isDirectory = true
        )

        val progressList = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertTrue(progressList.last().isComplete)
        assertEquals("content", File(targetDir, "folder/test.txt").readText())
    }

    @Test
    fun `moveFiles moves file and removes source`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("content")
        val sourceItem = createFileItem(path = sourceFile.absolutePath, name = "test.txt")

        val progressList = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        val finalProgress = progressList.last()
        assertTrue(finalProgress.isComplete)
        assertFalse(finalProgress.sourceDeleteFailed)
        assertTrue(File(targetDir, "test.txt").exists())
        assertFalse(sourceFile.exists())
    }

    @Test
    fun `moveFiles flags sourceDeleteFailed when source cannot be deleted`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("content")
        val sourceItem = createFileItem(path = sourceFile.absolutePath, name = "test.txt")

        // Make the source directory non-writable so deleting its child fails after the copy: on
        // Linux, unlinking a file requires write permission on the containing directory. Stands in
        // for the real trigger (a read-only-mounted SD/OTG volume). Skipped when the filesystem
        // does not enforce the permission (e.g. tests running as root).
        sourceDir.setWritable(false, false)
        try {
            assumeTrue("Filesystem does not enforce directory write permission", !sourceDir.canWrite())

            val progressList = repository.copyFiles(
                sources = listOf(sourceItem),
                targetDir = targetDir.absolutePath,
                deleteAfter = true,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()

            val finalProgress = progressList.last()
            assertTrue(finalProgress.isComplete)
            assertTrue(finalProgress.sourceDeleteFailed)
            assertTrue(File(targetDir, "test.txt").exists()) // copy succeeded
            assertTrue(sourceFile.exists()) // original was NOT deleted
        } finally {
            sourceDir.setWritable(true, false)
        }
    }

    @Test
    fun `moveFiles flags sourceDeleteFailed when a nested source cannot be deleted`() = runTest {
        val sourceDir = File(tempDir, "source")
        val subDir = File(sourceDir, "sub")
        val targetDir = File(tempDir, "target")
        subDir.mkdirs()
        targetDir.mkdirs()
        val nestedFile = File(subDir, "test.txt")
        nestedFile.writeText("content")
        val sourceItem = createFileItem(path = sourceDir.absolutePath, name = "source")

        // Make the innermost directory non-writable so its child can't be deleted; the failure must
        // propagate up through the recursive directory deletes to the final progress flag.
        subDir.setWritable(false, false)
        try {
            assumeTrue("Filesystem does not enforce directory write permission", !subDir.canWrite())

            val progressList = repository.copyFiles(
                sources = listOf(sourceItem),
                targetDir = targetDir.absolutePath,
                deleteAfter = true,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()

            val finalProgress = progressList.last()
            assertTrue(finalProgress.isComplete)
            assertTrue(finalProgress.sourceDeleteFailed)
            assertTrue(File(targetDir, "source/sub/test.txt").exists()) // copy succeeded
            assertTrue(nestedFile.exists()) // original was NOT deleted
            assertTrue(sourceDir.exists()) // non-empty source tree remains
        } finally {
            subDir.setWritable(true, false)
        }
    }

    @Test
    fun `copyFiles reports created paths recursively for a folder`() = runTest {
        val sourceDir = File(tempDir, "source")
        val subDir = File(sourceDir, "sub")
        val targetDir = File(tempDir, "target")
        subDir.mkdirs()
        targetDir.mkdirs()
        File(sourceDir, "top.txt").writeText("top")
        File(subDir, "nested.txt").writeText("nested")
        val sourceItem = createFileItem(path = sourceDir.absolutePath, name = "source")

        val finalProgress = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList().last()

        // scanFile does not recurse, so the repository must report each created child explicitly.
        assertEquals(
            setOf(
                File(targetDir, "source/top.txt").absolutePath,
                File(targetDir, "source/sub/nested.txt").absolutePath
            ),
            finalProgress.createdPaths.toSet()
        )
        assertTrue(finalProgress.deletedSourcePaths.isEmpty())
    }

    @Test
    fun `copyFiles reports the collision-resolved created path`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        File(sourceDir, "test.txt").writeText("source content")
        File(targetDir, "test.txt").writeText("existing content")
        val sourceItem = createFileItem(path = File(sourceDir, "test.txt").absolutePath, name = "test.txt")

        val finalProgress = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList().last()

        // The created path must be the file actually written, not the pre-existing colliding name.
        assertEquals(
            listOf(File(targetDir, "test (1).txt").absolutePath),
            finalProgress.createdPaths
        )
    }

    @Test
    fun `moveFiles reports deleted source paths recursively`() = runTest {
        val sourceDir = File(tempDir, "source")
        val subDir = File(sourceDir, "sub")
        val targetDir = File(tempDir, "target")
        subDir.mkdirs()
        targetDir.mkdirs()
        val topFile = File(sourceDir, "top.txt").apply { writeText("top") }
        val nestedFile = File(subDir, "nested.txt").apply { writeText("nested") }
        val sourceItem = createFileItem(path = sourceDir.absolutePath, name = "source")

        val finalProgress = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList().last()

        assertFalse(finalProgress.sourceDeleteFailed)
        // Every moved child's old location must be reported so its stale MediaStore row is removed.
        assertEquals(
            setOf(topFile.absolutePath, nestedFile.absolutePath),
            finalProgress.deletedSourcePaths.toSet()
        )
        assertEquals(
            setOf(
                File(targetDir, "source/top.txt").absolutePath,
                File(targetDir, "source/sub/nested.txt").absolutePath
            ),
            finalProgress.createdPaths.toSet()
        )
    }

    @Test
    fun `copyFiles reports paths in batches while the transfer runs`() = runTest {
        val sourceDir = File(tempDir, "source")
        val targetDir = File(tempDir, "target")
        sourceDir.mkdirs()
        targetDir.mkdirs()
        // More files than the repository holds before handing a batch over, so the paths cannot
        // all arrive on the final emission — holding one per copied file is what ran devices out
        // of heap. A caller reading only the last emission would miss every earlier batch.
        val fileCount = 501
        repeat(fileCount) { index -> File(sourceDir, "file_$index.txt").writeText("x") }
        val sourceItem = createFileItem(path = sourceDir.absolutePath, name = "source")

        val emissions = repository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        val createdBatches = emissions.map { it.createdPaths }.filter { it.isNotEmpty() }
        val deletedBatches = emissions.map { it.deletedSourcePaths }.filter { it.isNotEmpty() }
        assertTrue(createdBatches.size > 1)
        assertTrue(deletedBatches.size > 1)
        // Batched, not sampled: every created and every removed path is still reported exactly once.
        assertEquals(fileCount, createdBatches.sumOf { it.size })
        assertEquals(
            (0 until fileCount).map { File(targetDir, "source/file_$it.txt").absolutePath }.toSet(),
            createdBatches.flatten().toSet()
        )
        assertEquals(fileCount, deletedBatches.sumOf { it.size })
        assertEquals(
            (0 until fileCount).map { File(sourceDir, "file_$it.txt").absolutePath }.toSet(),
            deletedBatches.flatten().toSet()
        )
    }

    @Test
    fun `copyFiles throws SecurityException for target outside allowed roots`() = runTest {
        val sourceDir = File(tempDir, "source")
        sourceDir.mkdirs()
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("content")
        val sourceItem = createFileItem(path = sourceFile.absolutePath, name = "test.txt")
        val outsideDir = File(System.getProperty("java.io.tmpdir"), "outside_${System.currentTimeMillis()}")
        outsideDir.mkdirs()

        try {
            var exceptionThrown = false
            try {
                repository.copyFiles(
                    sources = listOf(sourceItem),
                    targetDir = outsideDir.absolutePath,
                    deleteAfter = false,
                    allowedRoots = listOf(tempDir.absolutePath)
                ).toList()
            } catch (e: SecurityException) {
                exceptionThrown = true
            }
            assertTrue(exceptionThrown)
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `copyFiles wraps IO error during transfer as FileTransferIOException`() = runTest {
        // A read that fails once the stream is open stands in for the unsimulatable real cause —
        // an EIO from removable storage unmounted mid-copy — which must surface as
        // FileTransferIOException, not a raw IOException, so the ViewModel treats it as
        // environmental and skips Crashlytics reporting.
        //
        // Driven through the open stream rather than through a source that cannot be opened at
        // all: that one is skipped now (`copyFiles skips a source it cannot open and copies the
        // rest` below), and this catch has to keep failing the transfer for everything else.
        val targetDir = File(tempDir, "target")
        targetDir.mkdirs()
        val source = File(tempDir, "secret.txt").apply { writeText("x") }
        givenReadingFails(source)

        var thrown: Throwable? = null
        try {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = targetDir.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        } catch (e: FileTransferIOException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertTrue(thrown?.cause is IOException)
        // Over the whole chain, not just the wrapper's message: the platform exception underneath
        // carries the absolute path of `secret.txt`, and a report follows the chain. The cause is
        // attached scrubbed, so the name does not survive. That the failing type survives as the
        // stand-in's message is pinned by ErrorScrubbingTest, which raises a type the carrier
        // cannot be confused with; here the fixture throws an IOException and the carrier is one.
        assertFalse(causeChainMessages(thrown).contains("secret.txt"))
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
        // The other half of the scrub: the stand-in carries the frame that actually threw, not the
        // catch block that built it, so a report still points at the failing call. Without the copy
        // the deepest trace would start in the scrubber itself.
        assertFalse(
            attachedCause(thrown).stackTrace.first().className.contains("ErrorScrubbing")
        )
        // The truncated destination is removed on the way out, so the file list never shows it
        // beside the complete copies.
        assertFalse(File(targetDir, "secret.txt").exists())
    }

    @Test
    fun `copyFiles skips a source it cannot open and copies the rest`() = runTest {
        // The compress fix's counterpart on the transfer path: `Android/data` on a removable
        // volume is listed and then denied, so a whole `Android/` selection used to end with
        // nothing copied. A source that vanished between the selection and the walk is
        // indistinguishable from that and stands in for it here. [isStorageUnavailable] runs for
        // real: a JVM open failure carries no errno, which is the answer that keeps a walk going.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val readable = File(tempDir, "kept.txt").apply { writeText("content") }
        val unopenable = File(tempDir, "ghost.txt")

        val emissions = repository.copyFiles(
            sources = listOf(fileItemFor(readable), createFileItem(path = unopenable.absolutePath, name = "ghost.txt")),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals("content", File(targetDir, "kept.txt").readText())
        // No empty placeholder stands in for the file that was skipped: the source is opened
        // before getUniqueTargetFile reserves a name, and that call creates the file it returns.
        assertFalse(File(targetDir, "ghost.txt").exists())

        val completion = emissions.last()
        assertTrue(completion.isComplete)
        assertEquals(1, completion.copiedFiles)
        assertEquals(1, completion.skippedFiles)
        assertEquals(listOf(File(targetDir, "kept.txt").absolutePath), completion.createdPaths)
    }

    @Test
    fun `a move of a folder holding an unreadable file reports skips and not a delete failure`() = runTest {
        // The flat case below passes with or without the rule this pins, because a top-level
        // skipped source has no parent in the walk. One level down it is a different outcome: the
        // folder still holds the file that was skipped, so its own delete fails — and reporting
        // that as sourceDeleteFailed would tell the user "Copied, but some originals could not be
        // deleted", claiming a copy that did not finish, and would suppress the MediaStore
        // notification for the files the move really did remove.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val folder = File(tempDir, "folder").apply { mkdirs() }
        val readable = File(folder, "kept.txt").apply { writeText("content") }
        val unreadable = File(folder, "denied.txt").apply { writeText("secret") }
        unreadable.setReadable(false, false)
        // Root ignores the permission bits, so the denial this test needs cannot be staged there.
        assumeTrue(!unreadable.canRead())

        val emissions = repository.copyFiles(
            sources = listOf(fileItemFor(folder)),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals("content", File(targetDir, "folder/kept.txt").readText())
        assertTrue(unreadable.exists())
        assertFalse(readable.exists())
        // The folder could not come away, and that is the expected outcome rather than a failure.
        assertTrue(folder.exists())

        val completion = emissions.last()
        assertEquals(1, completion.copiedFiles)
        assertEquals(1, completion.skippedFiles)
        assertFalse(completion.sourceDeleteFailed)
        // Still reported gone, which the sticky flag would have suppressed.
        assertEquals(listOf(readable.absolutePath), completion.deletedSourcePaths)
    }

    @Test
    fun `copyFiles wraps a failure to close the source as FileTransferIOException`() = runTest {
        // Closing the source is an I/O site of its own — libcore's close() rethrows the errno, and
        // a volume going away under an open descriptor fails there rather than in a read. It runs
        // after the bytes are written and, on a move, after the original is deleted, so leaving it
        // outside the wrapped region would report a transfer that in fact succeeded as a failure
        // and file a Crashlytics non-fatal for an environmental error.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val source = File(tempDir, "secret.txt").apply { writeText("x") }
        mockkConstructor(FileInputStream::class)
        every { anyConstructed<FileInputStream>().close() } throws
            IOException("${source.absolutePath}: close failed")

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = targetDir.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is FileTransferIOException)
        assertFalse(causeChainMessages(thrown).contains("secret.txt"))
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
    }

    @Test
    fun `a close that fails while the destination is reserved keeps the classified failure`() = runTest {
        // The source is opened before the destination is reserved, so a reservation that fails has
        // to step over an already-open stream. Closing it is an I/O site of its own, and a throw
        // out of that catch clause replaces the exception being propagated: the classified failure
        // is lost, FolderViewModel misses the catch that tells the user what to do about it, and
        // the environmental close error is filed as a non-fatal by its generic one instead.
        givenTheDiskIsFull(false)
        // Same staging as the destination-failure tests below: a target that is a regular file
        // makes createNewFile fail with ENOTDIR while the source stream is open.
        val target = File(tempDir, "not_a_directory").apply { writeText("x") }
        val source = File(tempDir, "secret.txt").apply { writeText("x") }
        mockkConstructor(FileInputStream::class)
        every { anyConstructed<FileInputStream>().close() } throws
            IOException("${source.absolutePath}: close failed")

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is DestinationNotWritableException)
        // Searched over the whole chain rather than read off `thrown` for the reason
        // [attachedCause] gives: the flow rethrows a copy, and stack trace recovery carries no
        // suppressed list onto it. Pinned by shape rather than by the staged message, for the
        // reason the destination-failure tests below give: the close failure is attached as the
        // stand-in, so what identifies it is the platform type's name and not the path the
        // fixture put in front of it.
        assertEquals(IOException::class.java.name, suppressedMessages(thrown).single())
    }

    @Test
    fun `a skip reports the errno behind it`() = runTest {
        // The errno is what says whether `isStorageUnavailable`'s set covers what devices really
        // produce, and it is the only thing about a failed open that may be reported at all — the
        // exception's own message is the user's absolute path. Null off device, where nothing
        // attaches one; the value itself is exercised by FileAccessTest.
        val readable = File(tempDir, "kept.txt").apply { writeText("content") }
        val unopenable = File(tempDir, "ghost.txt")

        val emissions = repository.compressFiles(
            sources = listOf(fileItemFor(readable), createFileItem(path = unopenable.absolutePath, name = "ghost.txt")),
            targetDir = tempDir.absolutePath,
            zipName = "archive.zip",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        val completion = emissions.last()
        assertEquals(1, completion.skippedFiles)
        assertNull(completion.skippedErrno)
    }

    @Test
    fun `a failed transfer hands over the paths it created and deleted`() = runTest {
        // The batch is held back until MEDIA_PATH_BATCH_SIZE or completion, and a failure reaches
        // neither: without the callback the files a move completed before it failed keep MediaStore
        // rows for sources that are gone, and the copies that arrived are never indexed. Reported
        // through a callback rather than a last emission because emitting once the flow is already
        // failing races the channel flowOn puts in between, and lands only sometimes.
        //
        // The failure is staged without a mock so that nothing here depends on a stub being visible
        // to the thread the walk runs on: the first source copies into a writable target, and the
        // second is a folder whose counterpart at the destination already exists and is read-only.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val moved = File(tempDir, "moved.txt").apply { writeText("content") }
        val folder = File(tempDir, "folder").apply { mkdirs() }
        File(folder, "doomed.txt").writeText("x")
        val blocked = File(targetDir, "folder").apply { mkdirs() }
        blocked.setWritable(false, false)
        // Root writes into a read-only directory regardless, so the failure cannot be staged there.
        assumeTrue(!blocked.canWrite())

        var createdReported: List<String>? = null
        var deletedReported: List<String>? = null
        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(moved), fileItemFor(folder)),
                targetDir = targetDir.absolutePath,
                deleteAfter = true,
                allowedRoots = listOf(tempDir.absolutePath),
                onPartialTransfer = { created, deleted, _, _ ->
                    createdReported = created.toList()
                    deletedReported = deleted.toList()
                }
            ).toList()
        }.exceptionOrNull()

        blocked.setWritable(true, true)

        assertNotNull(thrown)
        assertEquals(listOf(File(targetDir, "moved.txt").absolutePath), createdReported)
        assertEquals(listOf(moved.absolutePath), deletedReported)
    }

    @Test
    fun `a hand-off that throws does not replace the failure being reported`() = runTest {
        // The caller indexes files in this callback, and that work can fail. Whatever it raises,
        // the exception the caller has to see is the transfer's own. Staged on the same read-only
        // destination folder as the hand-off test above, which is the failure-path counterpart to
        // the cancellation one below.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val moved = File(tempDir, "moved.txt").apply { writeText("content") }
        val folder = File(tempDir, "folder").apply { mkdirs() }
        File(folder, "doomed.txt").writeText("x")
        val blocked = File(targetDir, "folder").apply { mkdirs() }
        blocked.setWritable(false, false)
        assumeTrue(!blocked.canWrite())

        var reportedDeleteFailed: Boolean? = null
        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(moved), fileItemFor(folder)),
                targetDir = targetDir.absolutePath,
                deleteAfter = true,
                allowedRoots = listOf(tempDir.absolutePath),
                onPartialTransfer = { _, _, _, sourceDeleteFailed ->
                    // Read before the throw, so the same test pins that the flag reaches the
                    // caller from the transfer rather than from the emissions it collected.
                    reportedDeleteFailed = sourceDeleteFailed
                    throw IllegalStateException("broken callback")
                }
            ).toList()
        }.exceptionOrNull()

        blocked.setWritable(true, true)

        assertNotNull(thrown)
        assertFalse(thrown is IllegalStateException)
        assertEquals(false, reportedDeleteFailed)
    }

    @Test
    fun `a directory the walk cannot list is counted rather than passed over`() = runTest {
        // The silent case this counter exists for. `list()` answers null and raises nothing, and
        // `totalFileCount` goes blind on the same directory, so the totals agree with each other
        // and a subtree that was never seen used to come out as a clean success.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val folder = File(tempDir, "folder").apply { mkdirs() }
        File(folder, "kept.txt").writeText("content")
        val denied = File(folder, "denied").apply { mkdirs() }
        File(denied, "unseen.txt").writeText("secret")
        denied.setReadable(false, false)
        // Root lists a directory whatever its bits say, so the denial cannot be staged there.
        assumeTrue(denied.list() == null)

        val emissions = repository.copyFiles(
            sources = listOf(fileItemFor(folder)),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        denied.setReadable(true, true)

        val completion = emissions.last()
        assertTrue(completion.isComplete)
        assertEquals(1, completion.copiedFiles)
        // Not folded into skippedFiles, which has to keep agreeing with totalFiles.
        assertEquals(0, completion.skippedFiles)
        assertEquals(1, completion.unreadableDirectories)
    }

    @Test
    fun `a cancelled transfer still hands over what it had moved`() = runTest {
        // Cancelling is how a long transfer usually ends, and the files it had already moved are as
        // real as any others: their originals are gone and their copies are at the destination, so
        // MediaStore has to hear about both. NonCancellable is what keeps the hand-off from being
        // cancelled at its first suspension point and leaving the caller's view of them as it was.
        //
        // Cancelled the way `a cancelled extraction still reports what it removed` cancels — by
        // stopping the collector mid-walk — rather than by throwing from it: with the buffer flowOn
        // puts in between, a collector that throws can find the walk already finished, and the
        // failure path never runs at all.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val smallSource = File(tempDir, "moved.txt").apply { writeText("content") }
        // Big enough that the transfer is still inside the write loop when the collector stops: one
        // emission goes out per buffer written, and the flow buffers a bounded number of them.
        File(tempDir, "large.bin").writeText("X".repeat(600_000))

        var createdReported: List<String>? = null
        var deletedReported: List<String>? = null
        runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(smallSource), fileItemFor(File(tempDir, "large.bin"))),
                targetDir = targetDir.absolutePath,
                deleteAfter = true,
                allowedRoots = listOf(tempDir.absolutePath),
                // Suspends, as the real callback does. One that returns without suspending runs
                // even on a cancelled job, so it could not tell whether the hand-off happens at all.
                onPartialTransfer = { created, deleted, _, _ ->
                    withContext(Dispatchers.IO) {
                        createdReported = created.toList()
                        deletedReported = deleted.toList()
                    }
                }
            )
                // The first source emits once for its single buffer, so the second item is the
                // first of the large file — by which point the first file is copied, its original
                // deleted, and both paths are sitting in the batch nothing has collected yet. That
                // they are still sitting there depends on MEDIA_PATH_BATCH_SIZE being larger than
                // one: a batch emission would have handed them over and started a fresh list.
                .drop(1)
                .first()
        }

        // Exactly the one file that finished, so this also pins that the truncated destination the
        // cancelled copy left behind is never handed to the caller to index.
        assertEquals(listOf(File(targetDir, "moved.txt").absolutePath), createdReported)
        assertEquals(listOf(smallSource.absolutePath), deletedReported)
    }

    @Test
    fun `a transfer that covered everything hands nothing over`() = runTest {
        // The callback is the failure path's only report, so a clean transfer must not invoke it —
        // its paths already arrived on the completion emission and would be scanned twice.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val source = File(tempDir, "kept.txt").apply { writeText("content") }

        var invoked = false
        repository.copyFiles(
            sources = listOf(fileItemFor(source)),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath),
            onPartialTransfer = { _, _, _, _ -> invoked = true }
        ).toList()

        assertFalse(invoked)
    }

    @Test
    fun `a transfer that skipped files on a volume that is gone fails instead of reporting a partial`() = runTest {
        // What the errno cannot answer. `File.list()` returning null raises nothing at all, and
        // whether a failed open even carries an errno is a property of the platform — so a walk
        // that lost something asks the volume itself, once, and a root that no longer stats is a
        // failure rather than a partial success.
        givenTheVolumeAnswers(false)
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val source = File(tempDir, "ghost.txt")

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(createFileItem(path = source.absolutePath, name = "ghost.txt")),
                targetDir = targetDir.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is FileTransferIOException)
    }

    @Test
    fun `a transfer that skipped files on a volume that still answers reports a partial success`() = runTest {
        // The other side of that probe, and the ordinary case: `Android/data` denies its entries on
        // a volume that is perfectly healthy, and the transfer must still come out a partial
        // success rather than a failure.
        givenTheVolumeAnswers(true)
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val readable = File(tempDir, "kept.txt").apply { writeText("content") }
        val unopenable = File(tempDir, "ghost.txt")

        val emissions = repository.copyFiles(
            sources = listOf(fileItemFor(readable), createFileItem(path = unopenable.absolutePath, name = "ghost.txt")),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertTrue(emissions.last().isComplete)
        assertEquals(1, emissions.last().skippedFiles)
    }

    @Test
    fun `copyFiles fails the transfer when the storage behind a source has gone away`() = runTest {
        // The other side of the skip. A volume that unmounts between two opens fails them all with
        // the same FileNotFoundException a denied file raises, and skipping on that would drop
        // every remaining source and still report a partial success — with the destination often
        // on a different volume that is perfectly healthy, so nothing else would fail either.
        // Stubbed because only an errno separates the two, and no JVM test can raise one.
        givenTheStorageIsUnavailable()
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val source = File(tempDir, "ghost.txt")

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(createFileItem(path = source.absolutePath, name = "ghost.txt")),
                targetDir = targetDir.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is FileTransferIOException)
        assertFalse(causeChainMessages(thrown).contains("ghost.txt"))
        // The failing type is what the stand-in keeps as its message, and here it is one the
        // carrier cannot be confused with — so this pins both halves of the scrub at once: the
        // path is gone and the type a triager needs is not.
        assertEquals(FileNotFoundException::class.java.name, attachedCause(thrown).message)
    }

    @Test
    fun `a move leaves the original of a source it cannot open where it is`() = runTest {
        // The rule that makes skipping safe on a move: the source delete is reached only by a file
        // that was copied first, so a file the OS would not let the app read keeps its original.
        // Deleting it would destroy the only copy — there is no undo in this app.
        val targetDir = File(tempDir, "target").apply { mkdirs() }
        val readable = File(tempDir, "kept.txt").apply { writeText("content") }
        val unreadable = File(tempDir, "denied.txt").apply { writeText("secret") }
        unreadable.setReadable(false, false)
        // Root ignores the permission bits, so the denial this test needs cannot be staged there.
        assumeTrue(!unreadable.canRead())

        val emissions = repository.copyFiles(
            sources = listOf(fileItemFor(readable), fileItemFor(unreadable)),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertTrue(unreadable.exists())
        assertFalse(readable.exists())
        assertFalse(File(targetDir, "denied.txt").exists())

        val completion = emissions.last()
        assertEquals(1, completion.copiedFiles)
        assertEquals(1, completion.skippedFiles)
        // A skipped source was never deleted, so nothing failed to delete: the move must not also
        // claim the read-only-volume failure, whose toast tells the user something different. The
        // nested case, where the skipped file keeps its parent directory from coming away, is
        // `a move of a folder holding an unreadable file reports skips and not a delete failure`
        // above.
        assertFalse(completion.sourceDeleteFailed)
        assertEquals(listOf(readable.absolutePath), completion.deletedSourcePaths)
    }

    // === compressFiles Tests ===

    @Test
    fun `compressFiles deletes the partial archive and wraps an IO failure as FileTransferIOException`() = runTest {
        // A read that fails once the stream is open stands in for the unsimulatable real cause —
        // an EIO from removable storage unmounted mid-archive — which must surface as
        // FileTransferIOException, not a raw IOException, so the ViewModel treats it as
        // environmental and skips Crashlytics reporting. The half-written archive may not be left
        // behind either.
        //
        // Driven through the open stream rather than through a source that cannot be opened at
        // all: that one is skipped now (`compressFiles skips a source it cannot open and keeps the rest of the
        // archive` below), and
        // this catch has to keep failing the archive for everything else.
        //
        // The full-disk branch of this catch is covered by `a full device during compression
        // surfaces as insufficient storage` in the full-device section below.
        val source = File(tempDir, "secret.txt").apply { writeText("x") }
        givenReadingFails(source)

        var thrown: Throwable? = null
        try {
            repository.compressFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = tempDir.absolutePath,
                zipName = "archive.zip",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        } catch (e: Throwable) {
            thrown = e
        }

        assertTrue(thrown is FileTransferIOException)
        assertTrue(thrown?.cause is IOException)
        assertFalse(causeChainMessages(thrown).contains("secret.txt"))
        // Pinned by shape as well as by name. The name search alone stopped catching a dropped
        // `.scrubbed()` when this fixture stopped raising the platform's own
        // FileNotFoundException, whose message is the absolute path — the mocked failure carries
        // no path to find. This is the form the sibling sites use for the same reason.
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
        assertFalse(File(tempDir, "archive.zip").exists())
    }

    @Test
    fun `compressFiles skips a source it cannot open and keeps the rest of the archive`() = runTest {
        // Scoped storage lets `list()` name the entries under `Android/data` on a removable volume
        // and then denies the open, so a whole `Android/` selection used to end with no archive at
        // all over a `.nomedia` the user never chose. A file that vanished between the selection
        // and the walk is indistinguishable from that and stands in for it here. Everything that
        // could be read has to reach the archive, and the skipped file has to be counted rather
        // than passed off as compressed.
        val readable = File(tempDir, "kept.txt").apply { writeText("content") }
        val unopenable = File(tempDir, "ghost.txt")

        val emissions = repository.compressFiles(
            sources = listOf(fileItemFor(readable), createFileItem(path = unopenable.absolutePath, name = "ghost.txt")),
            targetDir = tempDir.absolutePath,
            zipName = "archive.zip",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        val archive = File(tempDir, "archive.zip")
        assertTrue(archive.exists())

        // No empty entry stands in for the file that was skipped: the source is opened before the
        // entry is started, so a listing of the archive never shows it as an empty file.
        val entries = ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        assertEquals(setOf("kept.txt"), entries)

        val completion = emissions.last()
        assertTrue(completion.isComplete)
        assertEquals(1, completion.compressedFiles)
        assertEquals(1, completion.skippedFiles)
        // The skipped file was counted by the same walk that tallied the total, so the two together
        // say how much of the selection made it in.
        assertEquals(2, completion.totalFiles)
    }

    @Test
    fun `compressFiles fails the archive when the storage behind a source has gone away`() = runTest {
        // The other side of the skip, as `copyFiles fails the transfer when the storage behind a
        // source has gone away` is for transfers: an open failure that is the volume's problem
        // rather than one file's must delete the archive and report, not be counted as a skip and
        // shipped as a partial success.
        givenTheStorageIsUnavailable()
        val source = File(tempDir, "ghost.txt")

        val thrown = runCatching {
            repository.compressFiles(
                sources = listOf(createFileItem(path = source.absolutePath, name = "ghost.txt")),
                targetDir = tempDir.absolutePath,
                zipName = "archive.zip",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is FileTransferIOException)
        assertFalse(causeChainMessages(thrown).contains("ghost.txt"))
        assertFalse(File(tempDir, "archive.zip").exists())
    }

    @Test
    fun `compressFiles archives a directory tree under its own entry names`() = runTest {
        // The only unit coverage of addToZip's directory branch: every other compressFiles test
        // passes a plain or missing file, so the recursion is otherwise reached only by the
        // instrumentation suite, which is not part of the per-change loop. The dedupe that walk
        // applies is not covered here — this fixture has no repeated name, so it passes with or
        // without it; `distinctNames` below is what pins that rule.
        val folder = File(tempDir, "folder").apply { mkdirs() }
        File(folder, "top.txt").writeText("a")
        val nested = File(folder, "nested").apply { mkdirs() }
        File(nested, "deep.txt").writeText("b")

        repository.compressFiles(
            sources = listOf(fileItemFor(folder)),
            targetDir = tempDir.absolutePath,
            zipName = "archive.zip",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        val entries = ZipFile(File(tempDir, "archive.zip")).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }

        assertEquals(
            setOf("folder/", "folder/top.txt", "folder/nested/", "folder/nested/deep.txt"),
            entries
        )
    }

    @Test
    fun `compressFiles leaves a malformed archive entry unwrapped`() = runTest {
        // The carve-out in that same catch: a ZipException names an entry this code built wrong,
        // which is an app bug and has to stay reportable rather than be classified as
        // environmental alongside the I/O failures. Listing one source twice is what provokes it —
        // ZipOutputStream rejects the duplicate entry name.
        val source = File(tempDir, "note.txt").apply { writeText("x") }
        val sourceItem = createFileItem(path = source.absolutePath, name = "note.txt")

        val thrown = runCatching {
            repository.compressFiles(
                sources = listOf(sourceItem, sourceItem),
                targetDir = tempDir.absolutePath,
                zipName = "archive.zip",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is ZipException)
        assertFalse(File(tempDir, "archive.zip").exists())
    }

    // === Directory listing dedupe ===
    //
    // Every recursive walk in the repository goes through `dedupeInPlace`, so the rule below is
    // what stops a copy writing one source twice under a collision-renamed name, a delete counting
    // a successful removal as a failure, an archive dying on the entry name ZipOutputStream
    // rejects, and every total overcounting. Its trigger — `File.list()` returning a name twice —
    // is an OS behaviour no JVM test can produce, which is why `distinctNames` exists to take the
    // listing as an argument.

    @Test
    fun `distinctNames keeps the first of each repeated name and their order`() {
        val names = arrayOf("b.txt", "a.txt", "b.txt", "c.txt", "a.txt", "b.txt")

        assertEquals(listOf("b.txt", "a.txt", "c.txt"), repository.distinctNames(names))
        // The walk's own array is compacted in place; a caller's is not.
        assertEquals(6, names.size)
        assertEquals("b.txt", names[2])
    }

    @Test
    fun `distinctNames leaves a listing with no repeats as it is`() {
        val names = arrayOf("a.txt", "b.txt", "c.txt")

        assertEquals(names.toList(), repository.distinctNames(names))
    }

    @Test
    fun `distinctNames returns nothing for an empty listing`() {
        assertEquals(emptyList<String>(), repository.distinctNames(emptyArray()))
    }

    // === Full-device translation ===
    //
    // A full volume surfaces as an ErrnoException for ENOSPC nested in an IOException, which no JVM
    // test can produce; that half is covered on device by DiskSpaceTest. Stubbing [isNoSpaceLeft]
    // isolates the other half — that each site consults it, and that the operation comes out as
    // InsufficientStorageException, which the ViewModels turn into "not enough space" rather than
    // the generic failure toast that tells the user nothing they can act on.
    //
    // The failure driven into each site is a stand-in for the ENOSPC that cannot be simulated: what
    // matters is that it reaches the same catch, and the assertion is on the type that comes out.
    // One test per site that translates: creating the destination file, the copy's byte transfer,
    // the compression loop, and the extraction loop.

    @Test
    fun `a full device while creating the destination file surfaces as insufficient storage`() = runTest {
        givenTheDiskIsFull(true)
        // A target that is a regular file rather than a directory makes createNewFile fail with
        // ENOTDIR, in the same place a full volume would fail with ENOSPC.
        val target = File(tempDir, "not_a_directory").apply { writeText("x") }
        val source = File(tempDir, "source.txt").apply { writeText("x") }

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is InsufficientStorageException)
        // The sibling throw in the same catch as the one below, scrubbed for the same reason.
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
    }

    @Test
    fun `a create failure that is not a full device stays a destination failure`() = runTest {
        // The translation has to stay scoped to a full volume: a destination that cannot be written
        // for any other reason is not fixed by freeing space, and telling the user to do so would
        // send them after the wrong thing.
        givenTheDiskIsFull(false)
        val target = File(tempDir, "not_a_directory").apply { writeText("x") }
        val source = File(tempDir, "source.txt").apply { writeText("x") }

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is DestinationNotWritableException)
    }

    @Test
    fun `a destination failure names no file in its message and carries no platform cause`() = runTest {
        // A file name is personal data, and this one reaches a log or a crash report whenever a
        // caller reports the failure rather than handling it. Same setup as the test above:
        // `source.txt` is what a message built from the file being created would carry.
        //
        // The cause is pinned by shape rather than by searching the chain for `source.txt`, for the
        // reason the section header gives about ENOSPC: this JVM's createNewFile reports
        // "Not a directory" with no path at all, while Android's libcore rethrows the errno as
        // "<absolute path>: ENOTDIR", so the leak the scrub exists for cannot be reproduced
        // off-device. What is checkable here is that the attached cause is the stand-in and not the
        // platform exception — its message is the platform type's name, and the chain stops there.
        givenTheDiskIsFull(false)
        val target = File(tempDir, "not_a_directory").apply { writeText("x") }
        val source = File(tempDir, "source.txt").apply { writeText("x") }

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is DestinationNotWritableException)
        assertFalse(thrown?.message.orEmpty().contains("source.txt"))
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
    }

    @Test
    fun `a full device during the byte transfer surfaces as insufficient storage`() = runTest {
        givenTheDiskIsFull(true)
        // A read that fails once the transfer has started — the same catch a full volume reaches
        // when the write itself fails. Not the vanished source the other sites use: the transfer
        // skips a source it cannot open instead of failing on it, so that one would never reach
        // this catch. The negative case is `copyFiles wraps IO error during transfer as
        // FileTransferIOException` above, which runs the real isNoSpaceLeft over the same failure.
        val target = File(tempDir, "target").apply { mkdirs() }
        val source = File(tempDir, "secret.txt").apply { writeText("x") }
        givenReadingFails(source)

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is InsufficientStorageException)
        assertFalse(causeChainMessages(thrown).contains("secret.txt"))
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
    }

    @Test
    fun `a full device during compression surfaces as insufficient storage`() = runTest {
        givenTheDiskIsFull(true)
        // A read that fails once the archive has already been created, which is where a full volume
        // fails too. Not the vanished source the other sites use — compression skips a source it
        // cannot open instead of failing on it, so that one would never reach this catch. The
        // negative case is `compressFiles deletes the partial archive and wraps an IO failure as
        // FileTransferIOException`.
        val source = File(tempDir, "secret.txt").apply { writeText("x") }
        givenReadingFails(source)

        val thrown = runCatching {
            repository.compressFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = tempDir.absolutePath,
                zipName = "archive.zip",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is InsufficientStorageException)
        assertFalse(causeChainMessages(thrown).contains("secret.txt"))
        // Pinned by shape for the reason the sibling test above gives: the mocked failure carries
        // no path, so the name search cannot catch a dropped `.scrubbed()` on its own.
        assertEquals(IOException::class.java.name, attachedCause(thrown).message)
        // Translating the failure must not cost the cleanup: a half-written archive left behind is
        // indistinguishable from a complete one in the file list.
        assertFalse(File(tempDir, "archive.zip").exists())
    }

    @Test
    fun `a full device during extraction surfaces as insufficient storage`() = runTest {
        givenTheDiskIsFull(true)
        givenPlentyOfFreeSpace()
        val zipFile = zipWithCorruptEntry()
        val target = File(tempDir, "extracted").apply { mkdirs() }

        val thrown = runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is InsufficientStorageException)
        // Pinned by shape: zip4j's CRC failure names no file, so there is no name to search the
        // chain for. The stand-in is exactly java.io.IOException where every platform exception
        // reaching this catch is a subclass of it.
        assertEquals(IOException::class.java, attachedCause(thrown).javaClass)
    }

    @Test
    fun `an extraction failure that is not a full device is rethrown unchanged`() = runTest {
        // Everything else has to keep its own type: a corrupt archive is reported as such, and a
        // cancellation stays a cancellation, so neither is mistaken for a volume the user can free.
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val zipFile = zipWithCorruptEntry()
        val target = File(tempDir, "extracted").apply { mkdirs() }

        val thrown = runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertNotNull(thrown)
        assertFalse(thrown is InsufficientStorageException)
        assertTrue(target.list()?.isEmpty() == true)
    }

    /**
     * Every message on [thrown]'s cause chain, joined. What a crash report would carry:
     * `ErrorReporter.report` calls `recordException`, which transmits the whole chain, so a scrub
     * asserted over the outermost message alone would pass while the platform exception underneath
     * still named the user's file. Depth-bounded like `isNoSpaceLeft`, so a cyclic chain built by
     * a future wrap cannot hang the assertion.
     */
    private fun causeChainMessages(thrown: Throwable?): String =
        generateSequence(thrown) { it.cause }
            .take(MAX_CAUSE_CHAIN_DEPTH)
            .joinToString(separator = "\n") { "${it.javaClass.name}: ${it.message}" }

    /**
     * The deepest link on [thrown]'s cause chain — the stand-in the repository attaches in place of
     * the platform exception. Not simply `thrown.cause`: a flow rethrows through coroutine stack
     * trace recovery, which inserts a copy of the wrapper above the original and would be what that
     * returned.
     */
    private fun attachedCause(thrown: Throwable?): Throwable =
        generateSequence(thrown) { it.cause }
            .take(MAX_CAUSE_CHAIN_DEPTH)
            .last()

    /**
     * Every exception attached to a link on [thrown]'s cause chain — where a cleanup failure that
     * was not allowed to replace the failure being propagated ends up.
     */
    private fun suppressedMessages(thrown: Throwable?): List<String> =
        generateSequence(thrown) { it.cause }
            .take(MAX_CAUSE_CHAIN_DEPTH)
            .flatMap { it.suppressed.asSequence() }
            .map { it.message.orEmpty() }
            .toList()

    private fun givenTheDiskIsFull(full: Boolean) {
        mockkStatic(DISK_SPACE_FILE_CLASS)
        every { any<Throwable>().isNoSpaceLeft() } returns full
    }

    /**
     * Stages the one answer a JVM test cannot provoke. [isStorageUnavailable] reads an errno off an
     * [android.system.ErrnoException], which the stubbed `android.jar` cannot construct, so a real
     * failure here always carries none and the function answers false on its own — which is why
     * only the tests that need true stub anything, and the ones that expect a source to be skipped
     * run the real function. `FileAccessTest` covers the errno mapping on a device.
     */
    private fun givenTheStorageIsUnavailable() {
        mockkStatic(FILE_ACCESS_FILE_CLASS)
        every { any<Throwable>().isStorageUnavailable() } returns true
    }

    /**
     * Whether the volume a walk asks about after losing something still answers. [storageAnswersAt]
     * goes through [android.os.StatFs], which under the unit-test `android.jar` neither stats nor
     * fails, so without this every JVM test would see an available volume whatever it staged — and
     * the tests that want that answer say so rather than leaning on it.
     */
    private fun givenTheVolumeAnswers(answers: Boolean) {
        mockkStatic(STORAGE_AVAILABILITY_FILE_CLASS)
        every { storageAnswersAt(any()) } returns answers
    }

    /**
     * Makes a file read fail with an [IOException] once its stream is already open — the
     * mid-archive I/O error (a volume unmounted under an open descriptor) that no JVM test can
     * produce for real, and the only file-side failure compression still fails on now that a source
     * it cannot open at all is skipped.
     *
     * Stubs every [FileInputStream] constructed while it is in force, not one file's, so it says
     * what it means only in a test that reads a single source. `unmockkAll()` in [tearDown] keeps
     * it from reaching the next test.
     */
    private fun givenReadingFails(file: File) {
        mockkConstructor(FileInputStream::class)
        // The message interpolates the path the way libcore's own I/O failures do. Without that
        // there is no file name anywhere in the chain, and the assertions that none survives into
        // the reported cause would pass with `.scrubbed()` deleted from the production code.
        every { anyConstructed<FileInputStream>().read(any<ByteArray>()) } throws
            IOException("${file.absolutePath}: read failed")
    }

    private fun givenPlentyOfFreeSpace() {
        // uncompressFile pre-flights the volume with StatFs, an android.* class that throws "not
        // mocked" on the JVM before the extraction under test is ever reached.
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns Long.MAX_VALUE
    }

    /**
     * An archive whose single entry is stored uncompressed and then has one payload byte flipped,
     * so extracting it fails on the CRC check — inside the extraction loop, after the destination
     * file has already been created, which is where a full volume fails too.
     */
    private fun zipWithCorruptEntry(): File {
        val payload = PAYLOAD_MARKER.repeat(64).toByteArray()
        val zipFile = File(tempDir, "corrupt.zip")

        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
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

        // Stored entries are written verbatim, so the payload can be found by its own bytes; ISO
        // 8859-1 maps every byte to one character, which keeps the index a byte offset.
        val bytes = zipFile.readBytes()
        val offset = String(bytes, Charsets.ISO_8859_1).indexOf(PAYLOAD_MARKER)
        bytes[offset] = (bytes[offset].toInt() xor 0xFF).toByte()
        zipFile.writeBytes(bytes)

        return zipFile
    }

    // === uncompressFile Tests ===

    @Test
    fun `an entry resolving to the target itself is rejected when it is a file`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "Download").apply { mkdirs() }
        // Canonicalises to the target, so the containment check alone lets it through — but a file
        // is written under its own lexical parent, one level above the folder the user chose.
        val zipFile = zipWithEntries(mapOf("../Download" to "payload"))

        val thrown = runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is ZipSlipException)
        assertNull(tempDir.listFiles()?.firstOrNull { it.name.startsWith("Download (") })
    }

    @Test
    fun `an archive whose first entry names the target folder still extracts`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted").apply { mkdirs() }
        // Several archivers put "./" at the front. It names the target too, and unlike a file entry
        // it means what it says, so it has to stay allowed.
        val zipFile = zipWithEntries(mapOf("./" to "", "holiday.txt" to "content"))

        repository.uncompressFile(
            zipPath = zipFile.absolutePath,
            targetDir = target.absolutePath,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals("content", File(target, "holiday.txt").readText())
    }

    @Test
    fun `a directory entry that escapes the target is rejected`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted").apply { mkdirs() }
        // A directory entry may name the target, not leave it: mkdirs resolves ".." through the
        // kernel, so without the guard this creates the folder above the one the user chose.
        val zipFile = zipWithEntries(mapOf("../evil/" to ""))

        val thrown = runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is ZipSlipException)
        assertFalse(File(tempDir, "evil").exists())
    }

    @Test
    fun `a failed extraction removes the folder it created`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/holiday.txt" to "content"))
        val target = File(tempDir, "extracted").apply { mkdirs() }

        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }

        // The folder goes with the file it held: nothing in it was there before the extraction, so
        // it stands for everything below it and the rollback costs one name instead of one per file.
        assertFalse(File(target, "photos").exists())
    }

    @Test
    fun `a failed extraction leaves a folder that was already there and what it held`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted")
        val existingFolder = File(target, "photos").apply { mkdirs() }
        val existingFile = File(existingFolder, "mine.txt").apply { writeText("mine") }
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/holiday.txt" to "content"))

        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }

        // Only what the extraction added is undone. Removing the folder itself would take the
        // user's own files with it.
        assertTrue(existingFolder.exists())
        assertTrue(existingFile.exists())
        assertFalse(File(existingFolder, "holiday.txt").exists())
    }

    @Test
    fun `a failed extraction removes a subfolder it added to a folder that was already there`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted")
        val existingFolder = File(target, "photos").apply { mkdirs() }
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/2024/holiday.txt" to "content"))

        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }

        // The rollback follows the path down to the shallowest folder the extraction created, so a
        // failure leaves the folder the user had without the one it did not.
        assertTrue(existingFolder.exists())
        assertFalse(File(existingFolder, "2024").exists())
    }

    @Test
    fun `a failed extraction removes a folder declared by its own archive entry`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val zipFile = zipWithEntriesThenCorruptEntry(
            mapOf("photos/" to "", "photos/holiday.txt" to "content")
        )
        val target = File(tempDir, "extracted").apply { mkdirs() }

        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }

        assertFalse(File(target, "photos").exists())
    }

    @Test
    fun `a failed extraction removes a renamed file without touching the one it collided with`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted").apply { mkdirs() }
        val existingFile = File(target, "notes.txt").apply { writeText("mine") }
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("notes.txt" to "from the archive"))

        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }

        // The extraction wrote to the name getUniqueTargetFile resolved, and that is the name the
        // rollback has to remove — the user's own file was never this extraction's to delete.
        assertTrue(existingFile.exists())
        assertEquals("mine", existingFile.readText())
        assertEquals(listOf("notes.txt"), target.list()?.toList())
    }

    @Test
    fun `a failed extraction removes a file whose entry name resolves above where it lands`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        // A crafted name whose canonical form names the folder the file is written inside. Nothing
        // may be tracked by where the name resolves to rather than where the file was created, or a
        // failure leaves the extracted file sitting in the user's folder.
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/." to "content"))
        val target = File(tempDir, "extracted").apply { mkdirs() }

        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }

        val leftovers = target.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Failed extraction left files behind: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `uncompressFile reports extracted paths in batches while the extraction runs`() = runTest {
        givenPlentyOfFreeSpace()
        // More entries than the repository holds before handing a batch over, so the paths cannot
        // all arrive on the final emission — holding one per extracted file is what ran devices out
        // of heap. A caller reading only the last emission would miss every earlier batch.
        val entryCount = 501
        val zipFile = zipWithEntries((0 until entryCount).associate { "file_$it.txt" to "content $it" })
        val target = File(tempDir, "extracted").apply { mkdirs() }

        val emissions = repository.uncompressFile(
            zipPath = zipFile.absolutePath,
            targetDir = target.absolutePath,
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        val batches = emissions.map { it.extractedPaths }.filter { it.isNotEmpty() }
        assertTrue(batches.size > 1)
        // Batched, not sampled: every extracted path is still reported exactly once.
        assertEquals(entryCount, batches.sumOf { it.size })
        assertEquals(
            (0 until entryCount).map { File(target, "file_$it.txt").absolutePath }.toSet(),
            batches.flatten().toSet()
        )
    }

    @Test
    fun `a failed extraction reports the roots it removed to the caller`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/holiday.txt" to "content"))
        val target = File(tempDir, "extracted").apply { mkdirs() }

        val rolledBack = mutableListOf<String>()
        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath),
                onRolledBack = { rolledBack.addAll(it) }
            ).toList()
        }

        // Roots, exactly as the rollback tracks them: the folder stands for the file it held, so
        // the caller undoes a whole extracted tree without being handed its contents.
        assertEquals(
            listOf(File(target, "data.bin").absolutePath, File(target, "photos").absolutePath),
            rolledBack
        )
    }

    @Test
    fun `a failed extraction reports the file it added to an existing folder, not the folder`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted")
        val existingFolder = File(target, "photos").apply { mkdirs() }
        File(existingFolder, "mine.txt").writeText("mine")
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/holiday.txt" to "content"))

        val rolledBack = mutableListOf<String>()
        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath),
                onRolledBack = { rolledBack.addAll(it) }
            ).toList()
        }

        // The folder is still on disk holding the user's own file. Reporting it would tell a caller
        // that removes rows by prefix to drop that file's row too — and the file with it.
        assertEquals(
            listOf(
                File(target, "data.bin").absolutePath,
                File(existingFolder, "holiday.txt").absolutePath
            ),
            rolledBack
        )
    }

    @Test
    fun `a cancelled extraction still reports what it removed`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        // Big enough that the extraction is still inside the write loop when the collector stops:
        // one emission goes out per buffer written, and the flow buffers 64 of them.
        val zipFile = zipWithEntries(mapOf("photos/holiday.txt" to "X".repeat(600_000)))
        val target = File(tempDir, "extracted").apply { mkdirs() }

        val rolledBack = mutableListOf<String>()
        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath),
                // Suspends, as the real callback does. A callback that returns without suspending
                // runs even on a cancelled job, so it could not tell whether the rollback reports
                // one at all.
                onRolledBack = { withContext(Dispatchers.IO) { rolledBack.addAll(it) } }
            ).first()
        }

        // Cancelling is how a long extraction usually ends, and it rolls back everything extracted
        // so far — so this is the path the caller most needs to hear about.
        assertTrue(rolledBack.contains(File(target, "photos").absolutePath))
    }

    /**
     * The copy-side twin of the extraction rollback above, and the reason it matters more: the
     * destination of the file being written when the user cancels holds a truncated copy, and a
     * truncated file is indistinguishable from a complete one in the listing. If that `delete()`
     * regressed, a cancelled copy would leave a half-file sitting next to — or, after a collision
     * rename, shadowing — the user's real one, with no undo.
     */
    @Test
    fun `a cancelled copy removes the partially written destination file`() = runTest {
        // Big enough that the copy is still inside the write loop when the collector stops: one
        // emission goes out per 8 KB buffer, and the flow buffers 64 of them.
        val source = File(tempDir, "big.bin").apply { writeText("X".repeat(600_000)) }
        val target = File(tempDir, "target").apply { mkdirs() }

        runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).first()
        }

        assertFalse(
            "A cancelled copy must not leave a truncated file at the destination",
            File(target, "big.bin").exists()
        )
    }

    /**
     * A move is a copy followed by deleting the source, so cancelling one must not have deleted
     * anything: the copy never completed, and the bytes exist nowhere else.
     */
    @Test
    fun `a cancelled move leaves the source file intact`() = runTest {
        val content = "X".repeat(600_000)
        val source = File(tempDir, "big.bin").apply { writeText(content) }
        val target = File(tempDir, "target").apply { mkdirs() }

        runCatching {
            repository.copyFiles(
                sources = listOf(fileItemFor(source)),
                targetDir = target.absolutePath,
                deleteAfter = true,
                allowedRoots = listOf(tempDir.absolutePath)
            ).first()
        }

        assertTrue("A cancelled move must not delete the source", source.exists())
        assertEquals("A cancelled move must not truncate the source", content, source.readText())
        assertFalse(
            "A cancelled move must not leave a truncated file at the destination",
            File(target, "big.bin").exists()
        )
    }

    @Test
    fun `a failed extraction does not report a folder it never managed to create`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val target = File(tempDir, "extracted").apply { mkdirs() }
        // A file where the entry needs a folder: "photos/2024" is claimed as this extraction's own
        // before anything is created, and then cannot be created at all.
        File(target, "photos").writeText("mine")
        val zipFile = zipWithEntries(mapOf("photos/2024/holiday.txt" to "content"))

        val rolledBack = mutableListOf<String>()
        runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath),
                onRolledBack = { rolledBack.addAll(it) }
            ).toList()
        }

        // Only what the rollback actually removed may be reported. A caller drops rows by prefix,
        // and a media provider unlinks the file behind a row it drops, so a path still on disk
        // would take the file with it.
        assertEquals(emptyList<String>(), rolledBack)
    }

    @Test
    fun `a reporting callback that throws leaves the extraction failure intact`() = runTest {
        givenTheDiskIsFull(false)
        givenPlentyOfFreeSpace()
        val zipFile = zipWithEntriesThenCorruptEntry(mapOf("photos/holiday.txt" to "content"))
        val target = File(tempDir, "extracted").apply { mkdirs() }

        val thrown = runCatching {
            repository.uncompressFile(
                zipPath = zipFile.absolutePath,
                targetDir = target.absolutePath,
                allowedRoots = listOf(tempDir.absolutePath),
                onRolledBack = { throw IllegalStateException("broken callback") }
            ).toList()
        }.exceptionOrNull()

        // The caller decides what the user is told from the exception it catches. A cleanup that
        // failed must not turn a corrupt archive — or a cancellation — into something else.
        assertNotNull(thrown)
        assertFalse(thrown is IllegalStateException)
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
     * An archive holding [entries] followed by an entry corrupted the way [zipWithCorruptEntry]
     * corrupts its only one, so extraction fails after the entries before it have already landed in
     * the target — which is the state the rollback has to undo.
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

    // === searchFilesStreaming Tests ===

    @Test
    fun `searchFilesStreaming finds files by partial name match`() = runTest {
        File(tempDir, "test_file.txt").createNewFile()
        File(tempDir, "another_test.txt").createNewFile()
        File(tempDir, "unrelated.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.name.contains("test") })
    }

    @Test
    fun `searchFilesStreaming matches a wildcard query against whole names`() = runTest {
        File(tempDir, "log-2024.txt").createNewFile()
        File(tempDir, "log-2025.txt").createNewFile()
        File(tempDir, "log-2024.txt.bak").createNewFile()
        File(tempDir, "notes.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "log-*.txt",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        // The .bak is the point: a pattern names the whole file, where a substring would have
        // matched anything the name merely contains.
        assertEquals(setOf("log-2024.txt", "log-2025.txt"), results.map { it.name }.toSet())
    }

    @Test
    fun `searchFilesStreaming treats a query without wildcards as a substring`() = runTest {
        // The behaviour every query typed before wildcards existed relies on.
        File(tempDir, "log-2024.txt.bak").createNewFile()
        File(tempDir, "notes.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "log-2024",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(listOf("log-2024.txt.bak"), results.map { it.name })
    }

    @Test
    fun `searchFilesStreaming applies a wildcard query inside subdirectories`() = runTest {
        val nested = File(tempDir, "nested").apply { mkdirs() }
        File(nested, "IMG_7.jpg").createNewFile()
        File(nested, "IMG_42.jpg").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "IMG_?.jpg",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(listOf("IMG_7.jpg"), results.map { it.name })
    }

    @Test
    fun `searchFilesStreaming keeps regex syntax in a wildcard query literal`() = runTest {
        File(tempDir, "report (1) final.pdf").createNewFile()
        File(tempDir, "report 1 final.pdf").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "report (1)*",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(listOf("report (1) final.pdf"), results.map { it.name })
    }

    @Test
    fun `searchFilesStreaming keeps hidden files out of a leading-wildcard query`() = runTest {
        // A leading star matches a dotfile by pattern, so the hidden-file guard is the only thing
        // between `*` and every dotfile on the volume.
        File(tempDir, ".secret.txt").createNewFile()
        File(tempDir, "notes.txt").createNewFile()

        val hidden = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "*.txt",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(includeHidden = false)
        ).toList()

        assertEquals(listOf("notes.txt"), hidden.map { it.name })

        val shown = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "*.txt",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(includeHidden = true)
        ).toList()

        assertEquals(setOf(".secret.txt", "notes.txt"), shown.map { it.name }.toSet())
    }

    @Test
    fun `searchFilesStreaming is case insensitive`() = runTest {
        File(tempDir, "TEST.txt").createNewFile()
        File(tempDir, "Test.txt").createNewFile()
        File(tempDir, "test.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "TEST",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(3, results.size)
    }

    @Test
    fun `searchFilesStreaming with empty query matches all files`() = runTest {
        File(tempDir, "file.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(1, results.size)
    }

    @Test
    fun `searchFilesStreaming returns empty for path outside allowed roots`() = runTest {
        val outsideDir = File(System.getProperty("java.io.tmpdir"), "outside_search_${System.currentTimeMillis()}")
        outsideDir.mkdirs()
        File(outsideDir, "test.txt").createNewFile()

        try {
            val results = repository.searchFilesStreaming(
                rootPath = outsideDir.absolutePath,
                query = "test",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()

            assertTrue(results.isEmpty())
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `searchFilesStreaming respects maxResults limit`() = runTest {
        repeat(10) { i ->
            File(tempDir, "test_$i.txt").createNewFile()
        }

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            maxResults = 3
        ).toList()

        assertEquals(3, results.size)
    }

    @Test
    fun `searchFilesStreaming skips hidden files`() = runTest {
        File(tempDir, ".hidden_test.txt").createNewFile()
        File(tempDir, "visible_test.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(1, results.size)
        assertEquals("visible_test.txt", results[0].name)
    }

    @Test
    fun `searchFilesStreaming searches in subdirectories`() = runTest {
        val subDir = File(tempDir, "subdir")
        subDir.mkdirs()
        File(subDir, "nested_test.txt").createNewFile()
        File(tempDir, "root_test.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath)
        ).toList()

        assertEquals(2, results.size)
    }

    @Test
    fun `searchFilesStreaming with itemKind ANY includes folders`() = runTest {
        File(tempDir, "test_dir").mkdirs()
        File(tempDir, "test_file.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(itemKind = SearchItemKind.ANY)
        ).toList()

        assertEquals(2, results.size)
        assertTrue(results.any { it.isDirectory })
        assertTrue(results.any { !it.isDirectory })
    }

    @Test
    fun `searchFilesStreaming with itemKind FOLDERS returns only folders`() = runTest {
        File(tempDir, "test_dir").mkdirs()
        File(tempDir, "test_file.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(itemKind = SearchItemKind.FOLDERS)
        ).toList()

        assertEquals(1, results.size)
        assertEquals("test_dir", results[0].name)
        assertTrue(results[0].isDirectory)
    }

    @Test
    fun `searchFilesStreaming with itemKind FILES excludes folders`() = runTest {
        File(tempDir, "test_dir").mkdirs()
        File(tempDir, "test_file.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(itemKind = SearchItemKind.FILES)
        ).toList()

        assertEquals(1, results.size)
        assertEquals("test_file.txt", results[0].name)
        assertFalse(results[0].isDirectory)
    }

    @Test
    fun `searchFilesStreaming includes hidden files when includeHidden is true`() = runTest {
        File(tempDir, ".hidden_test.txt").createNewFile()
        File(tempDir, "visible_test.txt").createNewFile()

        val results = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(includeHidden = true)
        ).toList()

        assertEquals(2, results.size)
    }

    @Test
    fun `searchFilesStreaming descends into hidden folders when includeHidden is true`() = runTest {
        val hiddenDir = File(tempDir, ".secret")
        hiddenDir.mkdirs()
        File(hiddenDir, "test_inside.txt").createNewFile()

        val included = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(includeHidden = true)
        ).toList()

        assertEquals(1, included.size)
        assertEquals("test_inside.txt", included[0].name)

        val excluded = repository.searchFilesStreaming(
            rootPath = tempDir.absolutePath,
            query = "test",
            allowedRoots = listOf(tempDir.absolutePath),
            filters = SearchFilters(includeHidden = false)
        ).toList()

        assertTrue(excluded.isEmpty())
    }

    // === totalNodeCount Tests ===

    @Test
    fun `totalNodeCount counts files and directories recursively`() = runTest {
        val folder = File(tempDir, "collect")
        folder.mkdirs()
        File(folder, "file1.txt").createNewFile()
        File(folder, "file2.txt").createNewFile()
        val subFolder = File(folder, "sub")
        subFolder.mkdirs()
        File(subFolder, "nested.txt").createNewFile()
        val fileItem = createFileItem(
            path = folder.absolutePath,
            name = "collect",
            isDirectory = true
        )

        val count = repository.totalNodeCount(listOf(fileItem))

        // Three files and the two directories holding them.
        assertEquals(5, count)
    }

    @Test
    fun `totalNodeCount does not follow or count a symlink`() = runTest {
        val folder = File(tempDir, "nodes")
        folder.mkdirs()
        val target = File(tempDir, "linkTarget")
        target.mkdirs()
        File(target, "inside.txt").createNewFile()
        val link = File(folder, "link")
        val created = try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue(
            "Filesystem does not support symbolic links",
            created && Files.isSymbolicLink(link.toPath())
        )
        val fileItem = createFileItem(
            path = folder.absolutePath,
            name = "nodes",
            isDirectory = true
        )

        val count = repository.totalNodeCount(listOf(fileItem))

        // Only the folder itself: a symlink is neither counted nor descended into, matching the
        // walk that deletes the tree.
        assertEquals(1, count)
    }

    // === Malformed File Name Tests ===

    // File names whose bytes are not valid UTF-8 surface as unpaired surrogates, which
    // `File.toPath()` rejects with an InvalidPathException. These tests cover the `java.io`
    // fallback only: `Build.VERSION.SDK_INT` is 0 on the JVM, so the `java.nio` branch these
    // names used to crash in is exercised by EdgeCasesTest on a device instead.
    private val malformedName = "broken\uD800name.txt"

    @Test
    fun `totalSize handles a name that cannot be converted to a Path`() = runTest {
        val fileItem = createFileItem(
            path = File(tempDir, malformedName).absolutePath,
            name = malformedName
        )

        assertEquals(0L, repository.totalSize(listOf(fileItem)))
    }

    @Test
    fun `rename returns null for a source name that cannot be converted to a Path`() = runTest {
        val fileItem = createFileItem(
            path = File(tempDir, malformedName).absolutePath,
            name = malformedName
        )

        assertNull(repository.rename(fileItem, "fixed.txt"))
        assertFalse(File(tempDir, "fixed.txt").exists())
    }

    // === getZipInfo Tests ===

    @Test
    fun `getZipInfo returns info for valid zip`() = runTest {
        val zipFile = File(tempDir, "test.zip")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("file1.txt"))
            zos.write("content1".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("file2.txt"))
            zos.write("content2".toByteArray())
            zos.closeEntry()
        }

        val info = repository.getZipInfo(zipFile.absolutePath)

        assertEquals(2, info.entryCount)
        assertFalse(info.isEncrypted)
    }

    private fun createFileItem(
        path: String = "/storage/emulated/0/test.txt",
        name: String = "test.txt",
        isDirectory: Boolean = false,
        size: Long = 1024,
        lastModified: Long = System.currentTimeMillis(),
        createdTime: Long = System.currentTimeMillis()
    ) = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        createdTime = createdTime,
        mimeType = if (isDirectory) "" else "text/plain",
        childCount = if (isDirectory) 0 else null
    )

    private companion object {
        const val DISK_SPACE_FILE_CLASS = "com.mauriciotogneri.fileexplorer.data.util.DiskSpaceKt"
        const val FILE_ACCESS_FILE_CLASS = "com.mauriciotogneri.fileexplorer.data.util.FileAccessKt"
        const val STORAGE_AVAILABILITY_FILE_CLASS =
            "com.mauriciotogneri.fileexplorer.data.util.StorageAvailabilityKt"
        const val PAYLOAD_MARKER = "PAYLOAD-"
        const val MAX_CAUSE_CHAIN_DEPTH = 10
    }
}
