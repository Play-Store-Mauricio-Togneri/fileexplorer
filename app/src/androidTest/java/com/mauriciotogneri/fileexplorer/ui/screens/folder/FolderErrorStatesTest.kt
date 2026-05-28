package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.ui.components.DeleteProgressDialog
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.flow.toList
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FolderErrorStatesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var testDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_error_states_${System.currentTimeMillis()}")
        testDir.mkdirs()
        fileRepository = FileRepository()
        allowedRoots = listOf(testDir.absolutePath)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // region Rename Error Tests

    @Test
    fun rename_invalidName_fails() = runBlocking {
        val testFile = createTestFile(testDir, "original.txt", "content")
        val fileItem = FileItem.from(testFile)

        val result = fileRepository.rename(fileItem, "invalid/name")

        assertTrue("Original file should still exist", testFile.exists())
        assertTrue("Rename with slash should return null", result == null)
    }

    @Test
    fun rename_toExistingFolder_fails() = runBlocking {
        val testFile = createTestFile(testDir, "file1.txt", "content1")
        val existingFolder = File(testDir, "existing_folder")
        existingFolder.mkdirs()
        val fileItem = FileItem.from(testFile)

        val result = fileRepository.rename(fileItem, "existing_folder")

        assertTrue("Original file should still exist", testFile.exists())
        assertTrue("Existing folder should still exist", existingFolder.exists())
        assertTrue("Rename file to existing folder name should return null", result == null)
    }

    @Test
    fun rename_success_returnsResult() = runBlocking {
        val testFile = createTestFile(testDir, "old_name.txt", "content")
        val fileItem = FileItem.from(testFile)

        val result = fileRepository.rename(fileItem, "new_name.txt")

        assertTrue("Rename should succeed", result != null)
        assertEquals("new_name.txt", File(result!!.newPath).name)
        assertTrue("New file should exist", File(result.newPath).exists())
        assertFalse("Old file should not exist", testFile.exists())
    }

    // endregion

    // region Create Folder Error Tests

    @Test
    fun createFolder_invalidName_fails() = runBlocking {
        val success = fileRepository.createFolder(testDir.absolutePath, "invalid/name")

        assertFalse("Create folder with slash should fail", success)
    }

    @Test
    fun createFolder_existingName_fails() = runBlocking {
        val existingFolder = File(testDir, "existing")
        existingFolder.mkdirs()

        val success = fileRepository.createFolder(testDir.absolutePath, "existing")

        assertFalse("Create folder with existing name should fail", success)
    }

    @Test
    fun createFolder_success_createsFolder() = runBlocking {
        val success = fileRepository.createFolder(testDir.absolutePath, "new_folder")

        assertTrue("Create folder should succeed", success)
        assertTrue("New folder should exist", File(testDir, "new_folder").exists())
    }

    // endregion

    // region Delete Error Tests

    @Test
    fun deleteProgress_showsProgress() {
        val progress = DeleteProgress(
            currentFile = "deleting.txt",
            deletedFiles = 5,
            totalFiles = 15,
            failedFiles = 0
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("deleting.txt").assertIsDisplayed()
    }

    @Test
    fun deleteProgress_showsPartialFailure() {
        val progress = DeleteProgress(
            currentFile = "",
            deletedFiles = 13,
            totalFiles = 15,
            failedFiles = 2,
            isComplete = true
        )

        assertEquals("Should have 2 failed files", 2, progress.failedFiles)
        assertEquals("Should have 13 deleted files", 13, progress.deletedFiles)
    }

    @Test
    fun deleteWithProgress_reportsCorrectCounts() = runBlocking {
        val files = (1..5).map { i ->
            createTestFile(testDir, "file$i.txt", "content $i")
        }
        val items = files.map { FileItem.from(it) }

        val progressList = fileRepository.deleteWithProgress(items).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("Should delete all 5 files", 5, lastProgress.deletedFiles)
        assertEquals("Should have 0 failures", 0, lastProgress.failedFiles)
    }

    // endregion

    // region Uncompress Error Tests

    @Test
    fun uncompress_wrongPassword_throwsException() = runBlocking {
        val zipPath = createPasswordProtectedZip(
            testDir,
            "protected.zip",
            mapOf("secret.txt" to "content"),
            "correct_password"
        )

        val targetDir = File(testDir, "extract_target")
        targetDir.mkdirs()

        var exceptionThrown = false
        try {
            fileRepository.uncompressFile(
                zipPath = zipPath,
                targetDir = targetDir.absolutePath,
                password = "wrong_password",
                allowedRoots = allowedRoots
            ).toList()
        } catch (e: ZipException) {
            exceptionThrown = true
        }

        assertTrue("Should throw ZipException for wrong password", exceptionThrown)
    }

    @Test
    fun uncompress_noPassword_throwsException() = runBlocking {
        val zipPath = createPasswordProtectedZip(
            testDir,
            "protected_no_pw.zip",
            mapOf("secret.txt" to "content"),
            "the_password"
        )

        val targetDir = File(testDir, "extract_no_pw")
        targetDir.mkdirs()

        var exceptionThrown = false
        try {
            fileRepository.uncompressFile(
                zipPath = zipPath,
                targetDir = targetDir.absolutePath,
                password = null,
                allowedRoots = allowedRoots
            ).toList()
        } catch (e: ZipException) {
            exceptionThrown = true
        }

        assertTrue("Should throw ZipException when password required but not provided", exceptionThrown)
    }

    @Test
    fun uncompress_correctPassword_succeeds() = runBlocking {
        val password = "correct123"
        val zipPath = createPasswordProtectedZip(
            testDir,
            "protected_success.zip",
            mapOf("secret.txt" to "confidential"),
            password
        )

        val targetDir = File(testDir, "extract_success")
        targetDir.mkdirs()

        val progressList = fileRepository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = password,
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should complete successfully", lastProgress.isComplete)
        assertTrue("File should be extracted", File(targetDir, "secret.txt").exists())
    }

    // endregion

    // region Security Tests

    @Test
    fun copyFiles_outsideAllowedRoots_throwsSecurityException() = runBlocking {
        val sourceFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(sourceFile)

        var securityExceptionThrown = false
        try {
            fileRepository.copyFiles(
                sources = listOf(sourceItem),
                targetDir = "/data/local/tmp",
                deleteAfter = false,
                allowedRoots = allowedRoots
            ).toList()
        } catch (e: SecurityException) {
            securityExceptionThrown = true
        }

        assertTrue("Should throw SecurityException for target outside allowed roots", securityExceptionThrown)
    }

    @Test
    fun compressFiles_outsideAllowedRoots_throwsSecurityException() = runBlocking {
        val sourceFile = createTestFile(testDir, "compress_source.txt", "content")
        val sourceItem = FileItem.from(sourceFile)

        var securityExceptionThrown = false
        try {
            fileRepository.compressFiles(
                sources = listOf(sourceItem),
                targetDir = "/data/local/tmp",
                zipName = "test.zip",
                allowedRoots = allowedRoots
            ).toList()
        } catch (e: SecurityException) {
            securityExceptionThrown = true
        }

        assertTrue("Should throw SecurityException for target outside allowed roots", securityExceptionThrown)
    }

    @Test
    fun uncompressFile_outsideAllowedRoots_throwsSecurityException() = runBlocking {
        val zipPath = createTestZip(
            testDir,
            "test.zip",
            mapOf("file.txt" to "content")
        )

        var securityExceptionThrown = false
        try {
            fileRepository.uncompressFile(
                zipPath = zipPath,
                targetDir = "/data/local/tmp",
                password = null,
                allowedRoots = allowedRoots
            ).toList()
        } catch (e: SecurityException) {
            securityExceptionThrown = true
        }

        assertTrue("Should throw SecurityException for target outside allowed roots", securityExceptionThrown)
    }

    // endregion

    // region File Not Found Tests

    @Test
    fun listFiles_nonExistentPath_returnsEmptyList() = runBlocking {
        val nonExistentPath = File(testDir, "does_not_exist").absolutePath

        val files = fileRepository.listFiles(
            path = nonExistentPath,
            showHidden = false,
            sortMode = com.mauriciotogneri.fileexplorer.data.model.SortMode.NAME_ASC
        )

        assertTrue("Should return empty list for non-existent path", files.isEmpty())
    }

    @Test
    fun rename_nonExistentFile_fails() = runBlocking {
        val nonExistentFile = FileItem(
            path = File(testDir, "non_existent.txt").absolutePath,
            name = "non_existent.txt",
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            createdTime = 0L,
            mimeType = "text/plain",
            childCount = null
        )

        val result = fileRepository.rename(nonExistentFile, "new_name.txt")

        assertTrue("Rename of non-existent file should return null", result == null)
    }

    @Test
    fun delete_nonExistentFile_returnsTrue() = runBlocking {
        val nonExistentFile = FileItem(
            path = File(testDir, "non_existent.txt").absolutePath,
            name = "non_existent.txt",
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            createdTime = 0L,
            mimeType = "text/plain",
            childCount = null
        )

        val result = fileRepository.delete(listOf(nonExistentFile))

        assertFalse("Delete of non-existent file should return false", result)
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
        val tempDir = File(dir, "temp_zip_${System.currentTimeMillis()}")
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
