package com.mauriciotogneri.fileexplorer.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// device-required: the renames reach renameRegular's Files.move(ATOMIC_MOVE) branch, which needs a
// real SDK_INT, and the delete goes through the platform's own remove(3). Repository failure cases
// that need none of these — name validation, allowed-root checks, wrong or missing archive
// passwords — live in FileRepositoryTest and FileRepositoryOperationsTest.
@RunWith(AndroidJUnit4::class)
class FileRepositoryDeviceTest {

    private lateinit var testDir: File
    private lateinit var fileRepository: FileRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_error_states_${System.currentTimeMillis()}")
        testDir.mkdirs()
        fileRepository = FileRepository()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // region Rename Error Tests

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
    fun rename_toExistingHiddenFile_fails() = runBlocking {
        // Regression: on API 26+ the ATOMIC_MOVE rename path maps to rename(2), which silently
        // overwrites an existing target. A hidden dotfile is invisible to the dialog's collision
        // guard, so the repository must reject the rename and leave the hidden file untouched.
        val testFile = createTestFile(testDir, "visible.txt", "visible content")
        val hiddenFile = createTestFile(testDir, ".secret", "hidden content")
        val fileItem = FileItem.from(testFile)

        val result = fileRepository.rename(fileItem, ".secret")

        assertTrue("Rename onto existing hidden file should return null", result == null)
        assertTrue("Original file should still exist", testFile.exists())
        assertTrue("Hidden target file should still exist", hiddenFile.exists())
        assertEquals("Hidden file content must be preserved", "hidden content", hiddenFile.readText())
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

    // region File Not Found Tests

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

    /**
     * The product decision the previous version of this test said it was not pre-empting: a delete
     * is asked for a path that holds nothing afterwards, and one that already held nothing meets
     * that, so `deleteReturningErrno` resolves ENOENT to success and the user is no longer shown a
     * delete error for a file something else removed first.
     *
     * This is the one place that assertion is worth anything. `FileRepositoryTest` has to stand in
     * for [android.system.Os] on the JVM and can only assert its own stand-in; here the call goes
     * through the real `remove(3)` against a path that really is absent.
     */
    @Test
    fun delete_nonExistentFile_countsAsDone() = runBlocking {
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

        // On a device this goes through the real Os.remove, so it is the ENOENT rule itself being
        // asserted rather than the JVM stand-in FileRepositoryTest has to use: a path that already
        // holds nothing satisfies a delete, and the user is told nothing went wrong.
        assertTrue("Delete of a non-existent file must count as done", result.success)
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
