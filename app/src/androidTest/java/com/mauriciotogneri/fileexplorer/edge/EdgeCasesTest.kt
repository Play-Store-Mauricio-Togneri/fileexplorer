package com.mauriciotogneri.fileexplorer.edge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EdgeCasesTest {

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_edge_${System.currentTimeMillis()}")
        testDir.mkdirs()

        sourceDir = File(testDir, "source")
        sourceDir.mkdirs()

        targetDir = File(testDir, "target")
        targetDir.mkdirs()

        fileRepository = FileRepository()
        allowedRoots = listOf(testDir.absolutePath)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // region Empty Folder Operations

    @Test
    fun emptyFolder_createFolder_works() = runBlocking {
        val emptyFolder = File(sourceDir, "EmptyParent")
        emptyFolder.mkdirs()
        assertEquals("Parent should be empty", 0, emptyFolder.listFiles()?.size ?: -1)

        val success = fileRepository.createFolder(emptyFolder.absolutePath, "NewFolder")

        assertTrue("Create folder should succeed", success)
        assertTrue("New folder should exist", File(emptyFolder, "NewFolder").exists())
        assertTrue("New folder should be a directory", File(emptyFolder, "NewFolder").isDirectory)
    }

    @Test
    fun emptyFolder_createMultipleFolders_works() = runBlocking {
        val emptyFolder = File(sourceDir, "EmptyParent")
        emptyFolder.mkdirs()

        val success1 = fileRepository.createFolder(emptyFolder.absolutePath, "Folder1")
        val success2 = fileRepository.createFolder(emptyFolder.absolutePath, "Folder2")
        val success3 = fileRepository.createFolder(emptyFolder.absolutePath, "Folder3")

        assertTrue("First folder creation should succeed", success1)
        assertTrue("Second folder creation should succeed", success2)
        assertTrue("Third folder creation should succeed", success3)
        assertEquals("Should have 3 children", 3, emptyFolder.listFiles()?.size ?: -1)
    }

    // endregion

    // region Case-Only Rename Tests

    @Test
    fun caseOnlyRename_toLowercase_works() = runBlocking {
        val originalFile = createTestFile(sourceDir, "TestFile.txt", "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, "testfile.txt")

        assertNotNull("Rename should succeed", result)
        val renamedFile = File(sourceDir, "testfile.txt")
        assertTrue("Renamed file should exist", renamedFile.exists())
    }

    @Test
    fun caseOnlyRename_toUppercase_works() = runBlocking {
        val originalFile = createTestFile(sourceDir, "document.txt", "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, "DOCUMENT.TXT")

        assertNotNull("Rename should succeed", result)
        val renamedFile = File(sourceDir, "DOCUMENT.TXT")
        assertTrue("Renamed file should exist", renamedFile.exists())
    }

    @Test
    fun caseOnlyRename_mixedCase_works() = runBlocking {
        val originalFile = createTestFile(sourceDir, "readme.txt", "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, "ReadMe.TXT")

        assertNotNull("Rename should succeed", result)
        val renamedFile = File(sourceDir, "ReadMe.TXT")
        assertTrue("Renamed file should exist", renamedFile.exists())
    }

    @Test
    fun caseOnlyRename_folder_works() = runBlocking {
        val originalFolder = File(sourceDir, "documents")
        originalFolder.mkdirs()
        val folderItem = FileItem.from(originalFolder)

        val result = fileRepository.rename(folderItem, "Documents")

        assertNotNull("Folder rename should succeed", result)
        val renamedFolder = File(sourceDir, "Documents")
        assertTrue("Renamed folder should exist", renamedFolder.exists())
        assertTrue("Should still be a directory", renamedFolder.isDirectory)
    }

    // endregion

    // region Very Long Filename Tests

    @Test
    fun veryLongFileName_fileOperations_work() = runBlocking {
        val longName = "A".repeat(100) + ".txt"
        val longNameFile = createTestFile(sourceDir, longName, "content")

        assertTrue("Long filename file should be created", longNameFile.exists())

        val fileItem = FileItem.from(longNameFile)
        assertEquals("FileItem should have correct name", longName, fileItem.name)
    }

    @Test
    fun veryLongFileName_copy_works() = runBlocking {
        val longName = "B".repeat(100) + ".txt"
        val longNameFile = createTestFile(sourceDir, longName, "test content")
        val sourceItem = FileItem.from(longNameFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFile = File(targetDir, longName)
        assertTrue("File with long name should be copied", copiedFile.exists())
        assertEquals("Content should match", "test content", copiedFile.readText())
    }

    @Test
    fun veryLongFileName_rename_works() = runBlocking {
        val originalName = "original.txt"
        val longName = "C".repeat(100) + ".txt"
        val originalFile = createTestFile(sourceDir, originalName, "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, longName)

        assertNotNull("Rename to long name should succeed", result)
        val renamedFile = File(sourceDir, longName)
        assertTrue("Renamed file with long name should exist", renamedFile.exists())
    }

    // endregion

    // region Unicode Filename Tests

    @Test
    fun unicodeFileName_createAndRead_works() {
        val unicodeName = "文档_📁_αβγ.txt"
        val unicodeFile = createTestFile(sourceDir, unicodeName, "unicode content")

        assertTrue("Unicode filename file should exist", unicodeFile.exists())
        assertEquals("Content should be readable", "unicode content", unicodeFile.readText())
    }

    @Test
    fun unicodeFileName_emojiOnly_works() {
        val emojiName = "📄🎵🎬.txt"
        val emojiFile = createTestFile(sourceDir, emojiName, "emoji content")

        assertTrue("Emoji filename file should exist", emojiFile.exists())

        val fileItem = FileItem.from(emojiFile)
        assertEquals("FileItem should have correct emoji name", emojiName, fileItem.name)
    }

    @Test
    fun unicodeFileName_copy_works() = runBlocking {
        val unicodeName = "日本語_ファイル.txt"
        val unicodeFile = createTestFile(sourceDir, unicodeName, "japanese content")
        val sourceItem = FileItem.from(unicodeFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFile = File(targetDir, unicodeName)
        assertTrue("Unicode filename file should be copied", copiedFile.exists())
        assertEquals("Content should match", "japanese content", copiedFile.readText())
    }

    @Test
    fun unicodeFileName_rename_works() = runBlocking {
        val originalName = "original.txt"
        val unicodeName = "переименованный_файл.txt"
        val originalFile = createTestFile(sourceDir, originalName, "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, unicodeName)

        assertNotNull("Rename to unicode name should succeed", result)
        val renamedFile = File(sourceDir, unicodeName)
        assertTrue("Renamed file with unicode name should exist", renamedFile.exists())
    }

    @Test
    fun unicodeFileName_arabicRtl_works() {
        val arabicName = "ملف_عربي.txt"
        val arabicFile = createTestFile(sourceDir, arabicName, "arabic content")

        assertTrue("Arabic RTL filename file should exist", arabicFile.exists())

        val fileItem = FileItem.from(arabicFile)
        assertEquals("FileItem should have correct Arabic name", arabicName, fileItem.name)
    }

    // endregion

    // region Special Characters in Path Tests

    @Test
    fun specialCharactersInPath_spaces_navigationWorks() = runBlocking {
        val folderWithSpaces = File(sourceDir, "folder with spaces")
        folderWithSpaces.mkdirs()
        val fileInFolder = createTestFile(folderWithSpaces, "test.txt", "content")

        assertTrue("Folder with spaces should exist", folderWithSpaces.exists())
        assertTrue("File in folder with spaces should exist", fileInFolder.exists())

        val files = fileRepository.listFiles(folderWithSpaces.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find 1 file", 1, files.size)
        assertEquals("File name should match", "test.txt", files[0].name)
    }

    @Test
    fun specialCharactersInPath_hash_navigationWorks() = runBlocking {
        val folderWithHash = File(sourceDir, "folder#name")
        folderWithHash.mkdirs()
        val fileInFolder = createTestFile(folderWithHash, "test.txt", "content")

        assertTrue("Folder with hash should exist", folderWithHash.exists())
        assertTrue("File in folder with hash should exist", fileInFolder.exists())

        val files = fileRepository.listFiles(folderWithHash.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find 1 file", 1, files.size)
    }

    @Test
    fun specialCharactersInPath_ampersand_navigationWorks() = runBlocking {
        val folderWithAmpersand = File(sourceDir, "A & B")
        folderWithAmpersand.mkdirs()
        val fileInFolder = createTestFile(folderWithAmpersand, "test.txt", "content")

        assertTrue("Folder with ampersand should exist", folderWithAmpersand.exists())
        assertTrue("File in folder with ampersand should exist", fileInFolder.exists())

        val files = fileRepository.listFiles(folderWithAmpersand.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find 1 file", 1, files.size)
    }

    @Test
    fun specialCharactersInPath_parentheses_navigationWorks() = runBlocking {
        val folderWithParens = File(sourceDir, "folder (copy)")
        folderWithParens.mkdirs()
        val fileInFolder = createTestFile(folderWithParens, "test.txt", "content")

        assertTrue("Folder with parentheses should exist", folderWithParens.exists())
        assertTrue("File in folder with parentheses should exist", fileInFolder.exists())

        val files = fileRepository.listFiles(folderWithParens.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find 1 file", 1, files.size)
    }

    @Test
    fun specialCharactersInPath_copyOperation_works() = runBlocking {
        val folderWithSpecialChars = File(sourceDir, "folder #1 (copy) & more")
        folderWithSpecialChars.mkdirs()
        createTestFile(folderWithSpecialChars, "test.txt", "content")
        val folderItem = FileItem.from(folderWithSpecialChars)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFolder = File(targetDir, "folder #1 (copy) & more")
        assertTrue("Folder with special chars should be copied", copiedFolder.exists())
        assertTrue("File inside should be copied", File(copiedFolder, "test.txt").exists())
    }

    // endregion

    // region Deep Nesting Tests

    @Test
    fun deepNesting_navigation_works() = runBlocking {
        var currentDir = sourceDir
        val depth = 20

        for (i in 1..depth) {
            currentDir = File(currentDir, "level$i")
            currentDir.mkdirs()
        }

        createTestFile(currentDir, "deep_file.txt", "deep content")

        val files = fileRepository.listFiles(currentDir.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find file at deep level", 1, files.size)
        assertEquals("File name should match", "deep_file.txt", files[0].name)
    }

    @Test
    fun deepNesting_copy_works() = runBlocking {
        var currentDir = sourceDir
        val depth = 10

        for (i in 1..depth) {
            currentDir = File(currentDir, "nested$i")
            currentDir.mkdirs()
        }

        val deepFile = createTestFile(currentDir, "deep.txt", "content")
        val sourceItem = FileItem.from(deepFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFile = File(targetDir, "deep.txt")
        assertTrue("Deep nested file should be copied", copiedFile.exists())
    }

    @Test
    fun deepNesting_copyFolder_preservesStructure() = runBlocking {
        val level1 = File(sourceDir, "L1")
        level1.mkdirs()
        val level2 = File(level1, "L2")
        level2.mkdirs()
        val level3 = File(level2, "L3")
        level3.mkdirs()
        createTestFile(level3, "deep.txt", "content")

        val folderItem = FileItem.from(level1)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedL1 = File(targetDir, "L1")
        val copiedL2 = File(copiedL1, "L2")
        val copiedL3 = File(copiedL2, "L3")
        val copiedFile = File(copiedL3, "deep.txt")

        assertTrue("L1 should be copied", copiedL1.exists())
        assertTrue("L2 should be copied", copiedL2.exists())
        assertTrue("L3 should be copied", copiedL3.exists())
        assertTrue("Deep file should be copied", copiedFile.exists())
    }

    // endregion

    // region Filename Validation Tests

    @Test
    fun emptyFileName_createFolder_fails() = runBlocking {
        val success = fileRepository.createFolder(sourceDir.absolutePath, "")

        assertFalse("Create folder with empty name should fail", success)
    }

    @Test
    fun whitespaceOnlyName_createFolder_works() = runBlocking {
        // Note: On Linux/Android filesystems, whitespace-only folder names are valid.
        // The repository does not explicitly block whitespace-only names.
        val success = fileRepository.createFolder(sourceDir.absolutePath, "   ")

        // This succeeds because "   " is a valid folder name on the filesystem
        assertTrue("Create folder with whitespace-only name should succeed on filesystem", success)
        assertTrue("Folder should exist", File(sourceDir, "   ").exists())
    }

    @Test
    fun invalidCharacters_slashInName_createFolder_fails() = runBlocking {
        val success = fileRepository.createFolder(sourceDir.absolutePath, "invalid/name")

        assertFalse("Create folder with slash should fail", success)
    }

    @Test
    fun invalidCharacters_backslashInName_createFolder_fails() = runBlocking {
        val success = fileRepository.createFolder(sourceDir.absolutePath, "invalid\\name")

        assertFalse("Create folder with backslash should fail", success)
    }

    @Test
    fun reservedName_dot_createFolder_fails() = runBlocking {
        val success = fileRepository.createFolder(sourceDir.absolutePath, ".")

        assertFalse("Create folder with . name should fail", success)
    }

    @Test
    fun reservedName_doubleDot_createFolder_fails() = runBlocking {
        val success = fileRepository.createFolder(sourceDir.absolutePath, "..")

        assertFalse("Create folder with .. name should fail", success)
    }

    // endregion

    // region Many Files Performance Tests

    @Test
    fun manyFiles_listing_works() = runBlocking {
        val fileCount = 200

        for (i in 1..fileCount) {
            createTestFile(sourceDir, "file_$i.txt", "content $i")
        }

        val startTime = System.currentTimeMillis()
        val files = fileRepository.listFiles(sourceDir.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        val endTime = System.currentTimeMillis()

        assertEquals("Should list all files", fileCount, files.size)
        assertTrue("Listing should complete in reasonable time (<5s)", endTime - startTime < 5000)
    }

    @Test
    fun manyFiles_deleteMultiple_works() = runBlocking {
        val fileCount = 50

        val files = (1..fileCount).map { i ->
            createTestFile(sourceDir, "delete_$i.txt", "content $i")
        }
        val items = files.map { FileItem.from(it) }

        val progressList = fileRepository.deleteWithProgress(items).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("All files should be deleted", fileCount, lastProgress.deletedFiles)

        files.forEach { file ->
            assertFalse("File should be deleted: ${file.name}", file.exists())
        }
    }

    // endregion

    // region Large File Tests

    @Test
    fun largeFile_copy_completes() = runBlocking {
        val largeContent = "X".repeat(1_000_000)
        val largeFile = createTestFile(sourceDir, "large.txt", largeContent)
        val sourceItem = FileItem.from(largeFile)

        val startTime = System.currentTimeMillis()
        val progressList = fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()
        val endTime = System.currentTimeMillis()

        val lastProgress = progressList.last()
        assertTrue("Copy should complete", lastProgress.isComplete)
        assertEquals("All bytes should be copied", largeContent.length.toLong(), lastProgress.copiedBytes)

        val copiedFile = File(targetDir, "large.txt")
        assertTrue("Large file should be copied", copiedFile.exists())
        assertEquals("File size should match", largeFile.length(), copiedFile.length())
        assertTrue("Copy should complete in reasonable time (<30s)", endTime - startTime < 30000)
    }

    @Test
    fun largeFile_move_removesSource() = runBlocking {
        val largeContent = "Y".repeat(500_000)
        val largeFile = createTestFile(sourceDir, "large_move.txt", largeContent)
        val sourceItem = FileItem.from(largeFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        assertFalse("Source file should be deleted", largeFile.exists())
        val movedFile = File(targetDir, "large_move.txt")
        assertTrue("Moved file should exist", movedFile.exists())
    }

    // endregion

    // region Hidden Files Tests

    @Test
    fun hiddenFile_startsWithDot_works() = runBlocking {
        val hiddenFile = createTestFile(sourceDir, ".hidden", "secret content")

        assertTrue("Hidden file should exist", hiddenFile.exists())

        val files = fileRepository.listFiles(sourceDir.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find 1 file", 1, files.size)
        assertEquals("File name should be .hidden", ".hidden", files[0].name)
    }

    @Test
    fun hiddenFile_showHiddenFalse_notListed() = runBlocking {
        val hiddenFile = createTestFile(sourceDir, ".hidden", "secret content")
        val visibleFile = createTestFile(sourceDir, "visible.txt", "visible content")

        assertTrue("Hidden file should exist", hiddenFile.exists())
        assertTrue("Visible file should exist", visibleFile.exists())

        val files = fileRepository.listFiles(sourceDir.absolutePath, showHidden = false, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find only 1 visible file", 1, files.size)
        assertEquals("File name should be visible.txt", "visible.txt", files[0].name)
    }

    @Test
    fun hiddenFolder_startsWithDot_works() = runBlocking {
        val hiddenFolder = File(sourceDir, ".hidden_folder")
        hiddenFolder.mkdirs()
        createTestFile(hiddenFolder, "secret.txt", "content")

        assertTrue("Hidden folder should exist", hiddenFolder.exists())

        val files = fileRepository.listFiles(sourceDir.absolutePath, showHidden = true, sortMode = SortMode.NAME_ASC)
        assertEquals("Should find 1 folder", 1, files.size)
        assertEquals("Folder name should be .hidden_folder", ".hidden_folder", files[0].name)
        assertTrue("Should be a directory", files[0].isDirectory)
    }

    // endregion

    // region Multiple Copy Operation Tests

    @Test
    fun multipleCopy_differentTargets_works() = runBlocking {
        val file1 = createTestFile(sourceDir, "file1.txt", "content1")
        val file2 = createTestFile(sourceDir, "file2.txt", "content2")

        val target1 = File(targetDir, "target1")
        target1.mkdirs()
        val target2 = File(targetDir, "target2")
        target2.mkdirs()

        val item1 = FileItem.from(file1)
        val item2 = FileItem.from(file2)

        fileRepository.copyFiles(
            sources = listOf(item1),
            targetDir = target1.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        fileRepository.copyFiles(
            sources = listOf(item2),
            targetDir = target2.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("File1 should be copied to target1", File(target1, "file1.txt").exists())
        assertTrue("File2 should be copied to target2", File(target2, "file2.txt").exists())
    }

    // endregion

    // region Empty File Tests

    @Test
    fun emptyFile_copy_works() = runBlocking {
        val emptyFile = File(sourceDir, "empty.txt")
        emptyFile.createNewFile()
        assertEquals("File should be empty", 0, emptyFile.length())

        val sourceItem = FileItem.from(emptyFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFile = File(targetDir, "empty.txt")
        assertTrue("Empty file should be copied", copiedFile.exists())
        assertEquals("Copied file should also be empty", 0, copiedFile.length())
    }

    @Test
    fun emptyFile_rename_works() = runBlocking {
        val emptyFile = File(sourceDir, "empty_original.txt")
        emptyFile.createNewFile()
        val fileItem = FileItem.from(emptyFile)

        val result = fileRepository.rename(fileItem, "empty_renamed.txt")

        assertNotNull("Rename should succeed", result)
        assertTrue("Renamed empty file should exist", File(sourceDir, "empty_renamed.txt").exists())
    }

    // endregion

    // region Helper Methods

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    // endregion
}
