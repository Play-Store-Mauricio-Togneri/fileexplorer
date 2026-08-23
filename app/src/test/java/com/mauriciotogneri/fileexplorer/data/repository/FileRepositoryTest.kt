package com.mauriciotogneri.fileexplorer.data.repository

import android.os.StatFs
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.isNoSpaceLeft
import com.mauriciotogneri.fileexplorer.data.util.thumbnailDiskCacheKeyFor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipException
import java.util.zip.ZipFile

@OptIn(ExperimentalCoilApi::class)
class FileRepositoryTest {

    private val repository = FileRepository()
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
        val repository = FileRepository { notifications++ }

        repository.createFolder(tempDir.absolutePath, "child")

        assertEquals(1, notifications)
    }

    @Test
    fun `rename notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository { notifications++ }
        val file = File(tempDir, "before.txt").apply { writeText("x") }

        repository.rename(fileItemFor(file), "after.txt")

        assertEquals(1, notifications)
    }

    @Test
    fun `delete notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository { notifications++ }
        val file = File(tempDir, "gone.txt").apply { writeText("x") }

        repository.delete(listOf(fileItemFor(file)))

        assertEquals(1, notifications)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteWithProgress notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository { notifications++ }
        val file = File(tempDir, "gone.txt").apply { writeText("x") }

        repository.deleteWithProgress(listOf(fileItemFor(file))).toList()

        assertEquals(1, notifications)
    }

    @Test
    fun `copyFiles notifies that files were mutated`() = runTest {
        var notifications = 0
        val repository = FileRepository { notifications++ }
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
        val repository = FileRepository { notifications++ }
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
        val repository = FileRepository { existedWhenNotified = file.exists() }

        repository.delete(listOf(fileItemFor(file)))

        assertEquals(false, existedWhenNotified)
    }

    @Test
    fun `createFolder notifies only once the folder exists`() = runTest {
        val child = File(tempDir, "child")
        var existedWhenNotified: Boolean? = null
        val repository = FileRepository { existedWhenNotified = child.exists() }

        repository.createFolder(tempDir.absolutePath, "child")

        assertEquals(true, existedWhenNotified)
    }

    @Test
    fun `createFolder does not notify when the name is rejected`() = runTest {
        // Validation runs before anything reaches disk, so a still-correct cached size survives.
        var notifications = 0
        val repository = FileRepository { notifications++ }

        assertFalse(repository.createFolder(tempDir.absolutePath, "bad/name"))

        assertEquals(0, notifications)
    }

    @Test
    fun `rename notifies only once the file has moved`() = runTest {
        val file = File(tempDir, "before.txt").apply { writeText("x") }
        val renamed = File(tempDir, "after.txt")
        var movedWhenNotified: Boolean? = null
        val repository = FileRepository { movedWhenNotified = renamed.exists() && !file.exists() }

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
        val repository = FileRepository { existedWhenNotified = file.exists() }

        repository.deleteWithProgress(listOf(fileItemFor(file))).toList()

        assertEquals(false, existedWhenNotified)
    }

    @Test
    fun `deleteWithProgress notifies when collection stops before the tree is fully deleted`() = runTest {
        // A half-deleted tree matches the cached size even less than a fully deleted one, so an
        // abandoned operation has to invalidate too.
        val files = (1..5).map { index -> File(tempDir, "f$index.txt").apply { writeText("x") } }
        var notifications = 0
        val repository = FileRepository { notifications++ }

        repository.deleteWithProgress(files.map { fileItemFor(it) }).first()

        assertEquals(1, notifications)
    }

    @Test
    fun `reading does not notify`() = runTest {
        var notifications = 0
        val repository = FileRepository { notifications++ }
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
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val key = requireNotNull(thumbnailDiskCacheKeyFor(video))

        repository.delete(listOf(fileItemFor(video)))

        verify { diskCache.remove(key) }
    }

    @Test
    fun `deleteWithProgress drops the thumbnail cached for the file`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        val key = requireNotNull(thumbnailDiskCacheKeyFor(video))

        repository.deleteWithProgress(listOf(fileItemFor(video))).toList()

        verify { diskCache.remove(key) }
    }

    // A renamed file keeps its content but stops answering to the name its thumbnail is keyed by.
    @Test
    fun `rename drops the thumbnail cached under the old name`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
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
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
        val video = File(tempDir, "clip.mp4").apply { writeText("x") }
        File(tempDir, "taken.mp4").apply { writeText("y") }

        assertNull(repository.rename(fileItemFor(video), "taken.mp4"))

        verify(exactly = 0) { diskCache.remove(any()) }
    }

    // A move empties the source path just as a delete does, so the entry keyed to it is as dead.
    @Test
    fun `moving a file drops the thumbnail cached at its old path`() = runTest {
        val diskCache = mockk<DiskCache>(relaxed = true)
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
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
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
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
        val repository = FileRepository(thumbnailDiskCache = { diskCache })
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

    @Test
    fun `delete removes file successfully`() = runTest {
        val file = File(tempDir, "toDelete.txt")
        file.writeText("content")
        val fileItem = createFileItem(path = file.absolutePath, name = "toDelete.txt")

        val result = repository.delete(listOf(fileItem))

        assertTrue(result)
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

        assertTrue(result)
        assertFalse(folder.exists())
    }

    @Test
    fun `delete handles non-existent file gracefully`() = runTest {
        val fileItem = createFileItem(
            path = File(tempDir, "nonexistent.txt").absolutePath,
            name = "nonexistent.txt"
        )

        val result = repository.delete(listOf(fileItem))

        assertFalse(result)
    }

    @Test
    fun `delete multiple files returns true only if all succeed`() = runTest {
        val file1 = File(tempDir, "file1.txt")
        val file2 = File(tempDir, "file2.txt")
        file1.writeText("content1")
        file2.writeText("content2")
        val items = listOf(
            createFileItem(path = file1.absolutePath, name = "file1.txt"),
            createFileItem(path = file2.absolutePath, name = "file2.txt")
        )

        val result = repository.delete(items)

        assertTrue(result)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
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
        // A source that has vanished by the time the byte transfer starts (here: it never
        // existed) makes source.inputStream() throw once the target is already created. This
        // stands in for the unsimulatable real cause — an EIO from removable storage unmounted
        // mid-copy — which must surface as FileTransferIOException, not a raw IOException, so the
        // ViewModel treats it as environmental and skips Crashlytics reporting.
        val targetDir = File(tempDir, "target")
        targetDir.mkdirs()
        val missingSource = File(tempDir, "ghost.txt")
        val sourceItem = createFileItem(path = missingSource.absolutePath, name = "ghost.txt")

        var thrown: Throwable? = null
        try {
            repository.copyFiles(
                sources = listOf(sourceItem),
                targetDir = targetDir.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        } catch (e: FileTransferIOException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertTrue(thrown?.cause is IOException)
        assertFalse(thrown?.message.orEmpty().contains("ghost.txt"))
    }

    // === compressFiles Tests ===

    @Test
    fun `compressFiles deletes the partial archive and wraps an IO failure as FileTransferIOException`() = runTest {
        // A source that has vanished by the time the byte transfer starts (here: it never existed)
        // makes file.inputStream() throw after the archive has already been created on disk. This
        // stands in for the unsimulatable real cause — an EIO from removable storage unmounted
        // mid-archive — which must surface as FileTransferIOException, not a raw IOException, so
        // the ViewModel treats it as environmental and skips Crashlytics reporting. The
        // half-written archive may not be left behind either.
        //
        // The full-disk branch of this catch is covered by `a full device during compression
        // surfaces as insufficient storage` in the full-device section below.
        val missingSource = File(tempDir, "ghost.txt")
        val sourceItem = createFileItem(path = missingSource.absolutePath, name = "ghost.txt")

        var thrown: Throwable? = null
        try {
            repository.compressFiles(
                sources = listOf(sourceItem),
                targetDir = tempDir.absolutePath,
                zipName = "archive.zip",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        } catch (e: Throwable) {
            thrown = e
        }

        assertTrue(thrown is FileTransferIOException)
        assertTrue(thrown?.cause is IOException)
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
    fun `a destination failure names no file in its message`() = runTest {
        // A file name is personal data, and this one reaches a log or a crash report whenever a
        // caller reports the failure rather than handling it. Same setup as the test above:
        // `source.txt` is what a message built from the file being created would carry.
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

        assertFalse(thrown?.message.orEmpty().contains("source.txt"))
    }

    @Test
    fun `a full device during the byte transfer surfaces as insufficient storage`() = runTest {
        givenTheDiskIsFull(true)
        // A source that has vanished by the time the transfer starts makes source.inputStream()
        // throw once the target is already created — the same catch a full volume reaches when the
        // write itself fails. The negative case is `copyFiles wraps IO error during transfer as
        // FileTransferIOException` above, which runs the real isNoSpaceLeft over the same failure.
        val target = File(tempDir, "target").apply { mkdirs() }
        val missingSource = File(tempDir, "ghost.txt")

        val thrown = runCatching {
            repository.copyFiles(
                sources = listOf(createFileItem(path = missingSource.absolutePath, name = "ghost.txt")),
                targetDir = target.absolutePath,
                deleteAfter = false,
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is InsufficientStorageException)
    }

    @Test
    fun `a full device during compression surfaces as insufficient storage`() = runTest {
        givenTheDiskIsFull(true)
        // The same vanished source the other sites use: it throws once the archive has already been
        // created, which is where a full volume fails too. The negative case is `compressFiles
        // deletes the partial archive and wraps an IO failure as FileTransferIOException`.
        val missingSource = File(tempDir, "ghost.txt")

        val thrown = runCatching {
            repository.compressFiles(
                sources = listOf(createFileItem(path = missingSource.absolutePath, name = "ghost.txt")),
                targetDir = tempDir.absolutePath,
                zipName = "archive.zip",
                allowedRoots = listOf(tempDir.absolutePath)
            ).toList()
        }.exceptionOrNull()

        assertTrue(thrown is InsufficientStorageException)
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

    private fun givenTheDiskIsFull(full: Boolean) {
        mockkStatic(DISK_SPACE_FILE_CLASS)
        every { any<Throwable>().isNoSpaceLeft() } returns full
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
        const val PAYLOAD_MARKER = "PAYLOAD-"
    }
}
