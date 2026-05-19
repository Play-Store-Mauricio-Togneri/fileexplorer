package com.mauriciotogneri.fileexplorer.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileOperationExecutionTest {

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var fileRepository: FileRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_ops_${System.currentTimeMillis()}")
        testDir.mkdirs()

        sourceDir = File(testDir, "source")
        sourceDir.mkdirs()

        targetDir = File(testDir, "target")
        targetDir.mkdirs()

        fileRepository = FileRepository()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun copyFile_createsFileInTarget() = runBlocking {
        val testFile = createTestFile(sourceDir, "test.txt", "hello world")
        val sourceItem = FileItem.from(testFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val copiedFile = File(targetDir, "test.txt")
        assertTrue("Copied file should exist", copiedFile.exists())
        assertEquals("hello world", copiedFile.readText())
        assertTrue("Source file should still exist", testFile.exists())
    }

    @Test
    fun copyFile_preservesContent() = runBlocking {
        val content = "This is a test file with some content.\nLine 2\nLine 3"
        val testFile = createTestFile(sourceDir, "content.txt", content)
        val sourceItem = FileItem.from(testFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val copiedFile = File(targetDir, "content.txt")
        assertEquals(content, copiedFile.readText())
    }

    @Test
    fun moveFile_removesSourceFile() = runBlocking {
        val testFile = createTestFile(sourceDir, "move_me.txt", "content")
        val sourceItem = FileItem.from(testFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true
        ).toList()

        val movedFile = File(targetDir, "move_me.txt")
        assertTrue("Moved file should exist in target", movedFile.exists())
        assertFalse("Source file should be deleted", testFile.exists())
    }

    @Test
    fun copyFolder_copiesAllContents() = runBlocking {
        val folder = File(sourceDir, "MyFolder")
        folder.mkdirs()
        createTestFile(folder, "file1.txt", "content1")
        createTestFile(folder, "file2.txt", "content2")

        val subFolder = File(folder, "SubFolder")
        subFolder.mkdirs()
        createTestFile(subFolder, "nested.txt", "nested content")

        val folderItem = FileItem.from(folder)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val copiedFolder = File(targetDir, "MyFolder")
        assertTrue("Copied folder should exist", copiedFolder.exists())
        assertTrue("file1.txt should exist", File(copiedFolder, "file1.txt").exists())
        assertTrue("file2.txt should exist", File(copiedFolder, "file2.txt").exists())
        assertTrue("SubFolder should exist", File(copiedFolder, "SubFolder").exists())
        assertTrue("nested.txt should exist", File(copiedFolder, "SubFolder/nested.txt").exists())

        assertEquals("content1", File(copiedFolder, "file1.txt").readText())
        assertEquals("nested content", File(copiedFolder, "SubFolder/nested.txt").readText())
    }

    @Test
    fun moveFolder_removesSourceFolder() = runBlocking {
        val folder = File(sourceDir, "MoveFolder")
        folder.mkdirs()
        createTestFile(folder, "inside.txt", "content")

        val folderItem = FileItem.from(folder)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true
        ).toList()

        val movedFolder = File(targetDir, "MoveFolder")
        assertTrue("Moved folder should exist in target", movedFolder.exists())
        assertTrue("inside.txt should exist", File(movedFolder, "inside.txt").exists())
        assertFalse("Source folder should be deleted", folder.exists())
    }

    @Test
    fun copyMultipleFiles_copiesAllFiles() = runBlocking {
        val file1 = createTestFile(sourceDir, "multi1.txt", "one")
        val file2 = createTestFile(sourceDir, "multi2.txt", "two")
        val file3 = createTestFile(sourceDir, "multi3.txt", "three")

        val items = listOf(
            FileItem.from(file1),
            FileItem.from(file2),
            FileItem.from(file3)
        )

        fileRepository.copyFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        assertTrue(File(targetDir, "multi1.txt").exists())
        assertTrue(File(targetDir, "multi2.txt").exists())
        assertTrue(File(targetDir, "multi3.txt").exists())

        assertEquals("one", File(targetDir, "multi1.txt").readText())
        assertEquals("two", File(targetDir, "multi2.txt").readText())
        assertEquals("three", File(targetDir, "multi3.txt").readText())
    }

    @Test
    fun copyFile_existingName_createsUniqueFile() = runBlocking {
        val testFile = createTestFile(sourceDir, "duplicate.txt", "original")
        createTestFile(targetDir, "duplicate.txt", "existing")

        val sourceItem = FileItem.from(testFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val original = File(targetDir, "duplicate.txt")
        val renamed = File(targetDir, "duplicate (1).txt")

        assertTrue("Original file should still exist", original.exists())
        assertTrue("Renamed copy should exist", renamed.exists())
        assertEquals("existing", original.readText())
        assertEquals("original", renamed.readText())
    }

    @Test
    fun copyFile_multipleExisting_incrementsCounter() = runBlocking {
        val testFile = createTestFile(sourceDir, "counter.txt", "new")
        createTestFile(targetDir, "counter.txt", "v0")
        createTestFile(targetDir, "counter (1).txt", "v1")
        createTestFile(targetDir, "counter (2).txt", "v2")

        val sourceItem = FileItem.from(testFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val renamed = File(targetDir, "counter (3).txt")
        assertTrue("Should create counter (3).txt", renamed.exists())
        assertEquals("new", renamed.readText())
    }

    @Test
    fun copyProgress_reportsCorrectBytes() = runBlocking {
        val content = "A".repeat(1000)
        val testFile = createTestFile(sourceDir, "progress.txt", content)
        val sourceItem = FileItem.from(testFile)

        val progressList = fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should report completion", lastProgress.isComplete)
        assertEquals("Total bytes should match file size", 1000L, lastProgress.totalBytes)
        assertEquals("Copied bytes should match total", lastProgress.totalBytes, lastProgress.copiedBytes)
    }

    @Test
    fun copyProgress_reportsCurrentFileName() = runBlocking {
        val testFile = createTestFile(sourceDir, "named.txt", "content")
        val sourceItem = FileItem.from(testFile)

        val progressList = fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val inProgress = progressList.dropLast(1)
        assertTrue("Should have progress updates", inProgress.isNotEmpty())
        assertTrue(
            "Should report file name during copy",
            inProgress.any { it.currentFile == "named.txt" }
        )
    }

    @Test
    fun copyEmptyFolder_createsEmptyFolder() = runBlocking {
        val emptyFolder = File(sourceDir, "EmptyFolder")
        emptyFolder.mkdirs()

        val folderItem = FileItem.from(emptyFolder)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val copiedFolder = File(targetDir, "EmptyFolder")
        assertTrue("Empty folder should be copied", copiedFolder.exists())
        assertTrue("Should be a directory", copiedFolder.isDirectory)
        assertEquals("Should be empty", 0, copiedFolder.listFiles()?.size ?: -1)
    }

    @Test
    fun moveEmptyFolder_movesAndDeletesSource() = runBlocking {
        val emptyFolder = File(sourceDir, "MoveEmpty")
        emptyFolder.mkdirs()

        val folderItem = FileItem.from(emptyFolder)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true
        ).toList()

        val movedFolder = File(targetDir, "MoveEmpty")
        assertTrue("Moved folder should exist", movedFolder.exists())
        assertFalse("Source folder should be deleted", emptyFolder.exists())
    }

    @Test
    fun copyLargeFile_completesSuccessfully() = runBlocking {
        val largeContent = "X".repeat(100_000)
        val testFile = createTestFile(sourceDir, "large.txt", largeContent)
        val sourceItem = FileItem.from(testFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        val copiedFile = File(targetDir, "large.txt")
        assertTrue("Large file should be copied", copiedFile.exists())
        assertEquals("Content should match", largeContent.length.toLong(), copiedFile.length())
    }

    @Test
    fun copyMixedFilesAndFolders_copiesAll() = runBlocking {
        val file = createTestFile(sourceDir, "standalone.txt", "standalone")
        val folder = File(sourceDir, "Folder")
        folder.mkdirs()
        createTestFile(folder, "inside.txt", "inside")

        val items = listOf(
            FileItem.from(file),
            FileItem.from(folder)
        )

        fileRepository.copyFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            deleteAfter = false
        ).toList()

        assertTrue(File(targetDir, "standalone.txt").exists())
        assertTrue(File(targetDir, "Folder").exists())
        assertTrue(File(targetDir, "Folder/inside.txt").exists())
    }

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.writeText(content)
        return file
    }
}
