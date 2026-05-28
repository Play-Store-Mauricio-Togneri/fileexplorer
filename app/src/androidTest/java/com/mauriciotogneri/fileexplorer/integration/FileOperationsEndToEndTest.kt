package com.mauriciotogneri.fileexplorer.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileOperationsEndToEndTest {

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_e2e_${System.currentTimeMillis()}")
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

    // region Copy Conflict Resolution Tests

    @Test
    fun copyFile_sameName_createsUniqueFile() = runBlocking {
        val sourceFile = createTestFile(sourceDir, "document.txt", "new content")
        createTestFile(targetDir, "document.txt", "existing content")
        val sourceItem = FileItem.from(sourceFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val originalFile = File(targetDir, "document.txt")
        val copiedFile = File(targetDir, "document (1).txt")

        assertTrue("Original file should still exist", originalFile.exists())
        assertTrue("Copied file with unique name should exist", copiedFile.exists())
        assertEquals("existing content", originalFile.readText())
        assertEquals("new content", copiedFile.readText())
    }

    @Test
    fun copyFile_multipleConflicts_incrementsCounter() = runBlocking {
        val sourceFile = createTestFile(sourceDir, "file.txt", "new")
        createTestFile(targetDir, "file.txt", "v0")
        createTestFile(targetDir, "file (1).txt", "v1")
        createTestFile(targetDir, "file (2).txt", "v2")
        val sourceItem = FileItem.from(sourceFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFile = File(targetDir, "file (3).txt")
        assertTrue("Should create file (3).txt", copiedFile.exists())
        assertEquals("new", copiedFile.readText())
    }

    // endregion

    // region Move Tests

    @Test
    fun moveFile_removesSource() = runBlocking {
        val sourceFile = createTestFile(sourceDir, "move_me.txt", "content")
        val sourceItem = FileItem.from(sourceFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        val movedFile = File(targetDir, "move_me.txt")
        assertTrue("Moved file should exist in target", movedFile.exists())
        assertFalse("Source file should be deleted", sourceFile.exists())
    }

    @Test
    fun moveFile_sameName_createsUniqueName() = runBlocking {
        val sourceFile = createTestFile(sourceDir, "conflict.txt", "moved content")
        createTestFile(targetDir, "conflict.txt", "existing")
        val sourceItem = FileItem.from(sourceFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        val originalFile = File(targetDir, "conflict.txt")
        val movedFile = File(targetDir, "conflict (1).txt")

        assertTrue("Original file should still exist", originalFile.exists())
        assertTrue("Moved file should have unique name", movedFile.exists())
        assertEquals("existing", originalFile.readText())
        assertEquals("moved content", movedFile.readText())
        assertFalse("Source file should be deleted", sourceFile.exists())
    }

    // endregion

    // region Compress Tests

    @Test
    fun compressFiles_createsZip() = runBlocking {
        val file1 = createTestFile(sourceDir, "file1.txt", "content 1")
        val file2 = createTestFile(sourceDir, "file2.txt", "content 2")
        val items = listOf(FileItem.from(file1), FileItem.from(file2))

        val progressList = fileRepository.compressFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            zipName = "archive.zip",
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertTrue("Output path should be set", lastProgress.outputPath != null)

        val zipFile = File(lastProgress.outputPath!!)
        assertTrue("Zip file should exist", zipFile.exists())
        assertTrue("Zip file should have .zip extension", zipFile.name.endsWith(".zip"))
    }

    @Test
    fun compressFolder_includesAllContents() = runBlocking {
        val folder = File(sourceDir, "MyFolder")
        folder.mkdirs()
        createTestFile(folder, "inside1.txt", "content 1")
        createTestFile(folder, "inside2.txt", "content 2")
        val subFolder = File(folder, "SubFolder")
        subFolder.mkdirs()
        createTestFile(subFolder, "nested.txt", "nested content")

        val folderItem = FileItem.from(folder)

        val progressList = fileRepository.compressFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            zipName = "folder_archive.zip",
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        val zipPath = lastProgress.outputPath!!

        ZipFile(zipPath).use { zip ->
            val entries = zip.fileHeaders.map { it.fileName }
            assertTrue("Should contain MyFolder/", entries.any { it == "MyFolder/" })
            assertTrue("Should contain inside1.txt", entries.any { it.contains("inside1.txt") })
            assertTrue("Should contain inside2.txt", entries.any { it.contains("inside2.txt") })
            assertTrue("Should contain SubFolder/", entries.any { it.contains("SubFolder/") })
            assertTrue("Should contain nested.txt", entries.any { it.contains("nested.txt") })
        }
    }

    @Test
    fun compressFiles_progress_reportsCorrectly() = runBlocking {
        val content = "A".repeat(1000)
        val file = createTestFile(sourceDir, "progress_test.txt", content)
        val item = FileItem.from(file)

        val progressList = fileRepository.compressFiles(
            sources = listOf(item),
            targetDir = targetDir.absolutePath,
            zipName = "progress.zip",
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("Should have progress updates", progressList.size > 1)

        val lastProgress = progressList.last()
        assertTrue("Should report completion", lastProgress.isComplete)
        assertEquals("Total files should match", 1, lastProgress.totalFiles)
        assertEquals("Compressed files should match", 1, lastProgress.compressedFiles)
    }

    // endregion

    // region Uncompress Tests

    @Test
    fun uncompressZip_extractsAllFiles() = runBlocking {
        val zipPath = createTestZip(
            sourceDir,
            "test_archive.zip",
            mapOf(
                "file1.txt" to "content 1",
                "file2.txt" to "content 2",
                "folder/nested.txt" to "nested content"
            )
        )

        val progressList = fileRepository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = null,
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)

        assertTrue("file1.txt should be extracted", File(targetDir, "file1.txt").exists())
        assertTrue("file2.txt should be extracted", File(targetDir, "file2.txt").exists())
        assertTrue("folder should be extracted", File(targetDir, "folder").exists())
        assertTrue("nested.txt should be extracted", File(targetDir, "folder/nested.txt").exists())

        assertEquals("content 1", File(targetDir, "file1.txt").readText())
        assertEquals("nested content", File(targetDir, "folder/nested.txt").readText())
    }

    @Test
    fun uncompressZip_passwordProtected_requiresPassword() = runBlocking {
        val password = "secret123"
        val zipPath = createPasswordProtectedZip(
            sourceDir,
            "protected.zip",
            mapOf("secret.txt" to "confidential"),
            password
        )

        val progressList = fileRepository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = password,
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertTrue("secret.txt should be extracted", File(targetDir, "secret.txt").exists())
        assertEquals("confidential", File(targetDir, "secret.txt").readText())
    }

    @Test
    fun uncompressZip_wrongPassword_throwsException() = runBlocking {
        val correctPassword = "correct123"
        val wrongPassword = "wrong456"
        val zipPath = createPasswordProtectedZip(
            sourceDir,
            "password_test.zip",
            mapOf("test.txt" to "content"),
            correctPassword
        )

        var exceptionThrown = false
        try {
            fileRepository.uncompressFile(
                zipPath = zipPath,
                targetDir = targetDir.absolutePath,
                password = wrongPassword,
                allowedRoots = allowedRoots
            ).toList()
        } catch (e: ZipException) {
            exceptionThrown = true
        }

        assertTrue("Should throw ZipException for wrong password", exceptionThrown)
    }

    @Test
    fun uncompressZip_conflictingNames_createsUnique() = runBlocking {
        createTestFile(targetDir, "existing.txt", "already here")

        val zipPath = createTestZip(
            sourceDir,
            "conflict.zip",
            mapOf("existing.txt" to "from zip")
        )

        fileRepository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = null,
            allowedRoots = allowedRoots
        ).toList()

        val originalFile = File(targetDir, "existing.txt")
        val extractedFile = File(targetDir, "existing (1).txt")

        assertTrue("Original should remain", originalFile.exists())
        assertTrue("Extracted should have unique name", extractedFile.exists())
        assertEquals("already here", originalFile.readText())
        assertEquals("from zip", extractedFile.readText())
    }

    // endregion

    // region Delete With Progress Tests

    @Test
    fun deleteMultipleFiles_showsProgress() = runBlocking {
        val files = (1..15).map { i ->
            createTestFile(sourceDir, "file$i.txt", "content $i")
        }
        val items = files.map { FileItem.from(it) }

        val progressList = fileRepository.deleteWithProgress(items).toList()

        assertTrue("Should have progress updates", progressList.size > 1)

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("Total files should be 15", 15, lastProgress.totalFiles)
        assertEquals("All files should be deleted", 15, lastProgress.deletedFiles)
        assertEquals("No failures expected", 0, lastProgress.failedFiles)

        files.forEach { file ->
            assertFalse("File should be deleted: ${file.name}", file.exists())
        }
    }

    @Test
    fun deleteMultipleFiles_reportsCurrentFile() = runBlocking {
        val files = (1..5).map { i ->
            createTestFile(sourceDir, "delete_$i.txt", "content")
        }
        val items = files.map { FileItem.from(it) }

        val progressList = fileRepository.deleteWithProgress(items).toList()

        val fileNames = progressList.dropLast(1).map { it.currentFile }
        assertTrue(
            "Should report file names during deletion",
            fileNames.any { it.startsWith("delete_") }
        )
    }

    @Test
    fun deleteFolderWithContents_deletesRecursively() = runBlocking {
        val folder = File(sourceDir, "DeleteMe")
        folder.mkdirs()
        createTestFile(folder, "inside1.txt", "content")
        createTestFile(folder, "inside2.txt", "content")
        val subFolder = File(folder, "SubFolder")
        subFolder.mkdirs()
        createTestFile(subFolder, "nested.txt", "content")

        val folderItem = FileItem.from(folder)

        val progressList = fileRepository.deleteWithProgress(listOf(folderItem)).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertFalse("Folder should be deleted", folder.exists())
    }

    // endregion

    // region Copy Progress Tests

    @Test
    fun copyLargeFile_showsProgress() = runBlocking {
        val largeContent = "X".repeat(100_000)
        val testFile = createTestFile(sourceDir, "large.txt", largeContent)
        val sourceItem = FileItem.from(testFile)

        val progressList = fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("Should have multiple progress updates", progressList.size > 1)

        val lastProgress = progressList.last()
        assertTrue("Should report completion", lastProgress.isComplete)
        assertEquals("Total bytes should match", largeContent.length.toLong(), lastProgress.totalBytes)
        assertEquals("Copied bytes should match total", lastProgress.totalBytes, lastProgress.copiedBytes)
    }

    @Test
    fun copyProgress_reportsCurrentFileName() = runBlocking {
        val testFile = createTestFile(sourceDir, "tracked.txt", "content")
        val sourceItem = FileItem.from(testFile)

        val progressList = fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val inProgress = progressList.dropLast(1)
        assertTrue("Should have progress updates", inProgress.isNotEmpty())
        assertTrue(
            "Should report file name during copy",
            inProgress.any { it.currentFile == "tracked.txt" }
        )
    }

    // endregion

    // region Create Folder Tests

    @Test
    fun createFolderInEmptyFolder_works() = runBlocking {
        val emptyParent = File(sourceDir, "EmptyParent")
        emptyParent.mkdirs()
        assertEquals("Parent should be empty", 0, emptyParent.listFiles()?.size ?: -1)

        val success = fileRepository.createFolder(emptyParent.absolutePath, "NewFolder")

        assertTrue("Create folder should succeed", success)
        assertTrue("New folder should exist", File(emptyParent, "NewFolder").exists())
    }

    @Test
    fun createFolder_invalidChars_fails() = runBlocking {
        val success = fileRepository.createFolder(sourceDir.absolutePath, "invalid/name")

        assertFalse("Create folder with slash should fail", success)
    }

    // endregion

    // region Symlink Handling Tests

    @Test
    fun copyFolder_withRegularFiles_copiesSuccessfully() = runBlocking {
        val folder = File(sourceDir, "RegularFolder")
        folder.mkdirs()
        createTestFile(folder, "real_file.txt", "content")

        val folderItem = FileItem.from(folder)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFolder = File(targetDir, "RegularFolder")
        assertTrue("Folder should be copied", copiedFolder.exists())
        assertTrue("Real file should be copied", File(copiedFolder, "real_file.txt").exists())
        assertEquals("content", File(copiedFolder, "real_file.txt").readText())
    }

    // endregion

    // region Compress Progress Tests

    @Test
    fun compressMultipleFiles_progress_tracksCorrectly() = runBlocking {
        val files = (1..5).map { i ->
            createTestFile(sourceDir, "compress_$i.txt", "content $i")
        }
        val items = files.map { FileItem.from(it) }

        val progressList = fileRepository.compressFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            zipName = "multi.zip",
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("Should have progress updates", progressList.size > 1)

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("Should compress all files", 5, lastProgress.compressedFiles)
    }

    // endregion

    // region Uncompress Progress Tests

    @Test
    fun uncompressZip_progress_tracksCorrectly() = runBlocking {
        val zipPath = createTestZip(
            sourceDir,
            "progress_test.zip",
            mapOf(
                "file1.txt" to "content 1",
                "file2.txt" to "content 2",
                "file3.txt" to "content 3"
            )
        )

        val progressList = fileRepository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = null,
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("Should have progress updates", progressList.size > 1)

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("Should extract all files", 3, lastProgress.extractedFiles)
        assertEquals("Extracted paths should be tracked", 3, lastProgress.extractedPaths.size)
    }

    // endregion

    // region Cancellation Tests

    @Test
    fun copyOperation_cancellation_throwsCancellationException() = runBlocking {
        val largeContent = "X".repeat(500_000)
        val testFile = createTestFile(sourceDir, "cancel_test.txt", largeContent)
        val sourceItem = FileItem.from(testFile)

        var cancellationThrown = false

        val job = launch {
            try {
                fileRepository.copyFiles(
                    sources = listOf(sourceItem),
                    targetDir = targetDir.absolutePath,
                    deleteAfter = false,
                    allowedRoots = allowedRoots
                ).collect { progress ->
                    if (progress.copiedBytes > 1000) {
                        cancel("Test cancellation")
                    }
                }
            } catch (e: CancellationException) {
                cancellationThrown = true
            }
        }

        job.join()
        assertTrue("Should throw CancellationException", cancellationThrown)
    }

    // endregion

    // region Copy/Move Mixed Files and Folders

    @Test
    fun copyMixedFilesAndFolders_copiesAll() = runBlocking {
        val file = createTestFile(sourceDir, "standalone.txt", "standalone content")
        val folder = File(sourceDir, "MyFolder")
        folder.mkdirs()
        createTestFile(folder, "inside.txt", "inside content")

        val items = listOf(
            FileItem.from(file),
            FileItem.from(folder)
        )

        fileRepository.copyFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("Standalone file should be copied", File(targetDir, "standalone.txt").exists())
        assertTrue("Folder should be copied", File(targetDir, "MyFolder").exists())
        assertTrue("File inside folder should be copied", File(targetDir, "MyFolder/inside.txt").exists())
        assertEquals("standalone content", File(targetDir, "standalone.txt").readText())
        assertEquals("inside content", File(targetDir, "MyFolder/inside.txt").readText())
    }

    @Test
    fun moveMixedFilesAndFolders_removesSource() = runBlocking {
        val file = createTestFile(sourceDir, "move_file.txt", "file content")
        val folder = File(sourceDir, "MoveFolder")
        folder.mkdirs()
        createTestFile(folder, "inside.txt", "folder content")

        val items = listOf(
            FileItem.from(file),
            FileItem.from(folder)
        )

        fileRepository.copyFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("File should exist in target", File(targetDir, "move_file.txt").exists())
        assertTrue("Folder should exist in target", File(targetDir, "MoveFolder").exists())
        assertFalse("Source file should be deleted", file.exists())
        assertFalse("Source folder should be deleted", folder.exists())
    }

    // endregion

    // region Empty Content Tests

    @Test
    fun copyEmptyFolder_createsEmptyFolder() = runBlocking {
        val emptyFolder = File(sourceDir, "EmptyFolder")
        emptyFolder.mkdirs()

        val folderItem = FileItem.from(emptyFolder)

        fileRepository.copyFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
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
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        val movedFolder = File(targetDir, "MoveEmpty")
        assertTrue("Moved folder should exist", movedFolder.exists())
        assertFalse("Source folder should be deleted", emptyFolder.exists())
    }

    @Test
    fun compressEmptyFolder_createsValidZip() = runBlocking {
        val emptyFolder = File(sourceDir, "EmptyToCompress")
        emptyFolder.mkdirs()

        val folderItem = FileItem.from(emptyFolder)

        val progressList = fileRepository.compressFiles(
            sources = listOf(folderItem),
            targetDir = targetDir.absolutePath,
            zipName = "empty_folder.zip",
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)

        val zipFile = File(lastProgress.outputPath!!)
        assertTrue("Zip file should exist", zipFile.exists())

        ZipFile(zipFile).use { zip ->
            assertTrue(
                "Should contain folder entry",
                zip.fileHeaders.any { it.fileName.contains("EmptyToCompress") }
            )
        }
    }

    // endregion

    // region Helper Methods

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun createTestZip(dir: File, name: String, entries: Map<String, String>): String {
        val zipFile = File(dir, name)
        val tempDir = File(dir, "temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            entries.forEach { (entryName, content) ->
                val file = File(tempDir, entryName)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }

            ZipFile(zipFile).use { zip ->
                val params = ZipParameters().apply {
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                }
                tempDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        zip.addFolder(file, params)
                    } else {
                        zip.addFile(file, params)
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }

        return zipFile.absolutePath
    }

    private fun createPasswordProtectedZip(
        dir: File,
        name: String,
        entries: Map<String, String>,
        password: String
    ): String {
        val zipFile = File(dir, name)
        val tempDir = File(dir, "temp_pwd_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            entries.forEach { (entryName, content) ->
                val file = File(tempDir, entryName)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }

            ZipFile(zipFile, password.toCharArray()).use { zip ->
                val params = ZipParameters().apply {
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                }
                tempDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        zip.addFolder(file, params)
                    } else {
                        zip.addFile(file, params)
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }

        return zipFile.absolutePath
    }

    // endregion
}
