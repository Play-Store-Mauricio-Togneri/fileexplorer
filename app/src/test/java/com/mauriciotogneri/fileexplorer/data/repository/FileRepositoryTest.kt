package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.model.SortMode
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

        assertEquals(3, repository.countChildren(dir.absolutePath))
    }

    @Test
    fun `countChildren counts hidden entries too`() = runTest {
        val dir = File(tempDir, "dir")
        dir.mkdirs()
        File(dir, "visible.txt").createNewFile()
        File(dir, ".hidden").createNewFile()

        assertEquals(2, repository.countChildren(dir.absolutePath))
    }

    @Test
    fun `countChildren returns zero for empty directory`() = runTest {
        val dir = File(tempDir, "empty")
        dir.mkdirs()

        assertEquals(0, repository.countChildren(dir.absolutePath))
    }

    @Test
    fun `countChildren returns null for non-existent path`() = runTest {
        val nonExistent = File(tempDir, "missing")

        assertNull(repository.countChildren(nonExistent.absolutePath))
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

        assertTrue(progressList.last().isComplete)
        assertTrue(File(targetDir, "test.txt").exists())
        assertFalse(sourceFile.exists())
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

    // === collectAllPaths Tests ===

    @Test
    fun `collectAllPaths returns all file paths recursively`() = runTest {
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

        val paths = repository.collectAllPaths(listOf(fileItem))

        assertEquals(5, paths.size)
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
}
