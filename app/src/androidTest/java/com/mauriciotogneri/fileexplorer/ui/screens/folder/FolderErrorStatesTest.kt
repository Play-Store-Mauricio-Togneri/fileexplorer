package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// device-required: the renames reach renameRegular's Files.move(ATOMIC_MOVE) branch, which needs a
// real SDK_INT, the delete goes through the platform's own remove(3), and the partial-failure case
// renders the delete dialog. Repository failure cases that need none of these — name validation,
// allowed-root checks, wrong or missing archive passwords — live in FileRepositoryTest and
// FileRepositoryOperationsTest.
@RunWith(AndroidJUnit4::class)
class FolderErrorStatesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

    // region Delete Error Tests

    /**
     * A partially-failed delete must still report real progress rather than reading as a total
     * failure. This used to assert only that `DeleteProgress` returns its own constructor
     * arguments — it never rendered the dialog, so the display it is named after was untested.
     *
     * The fraction is deleted-of-total, so the two failures must not be counted into it: with
     * 6 deleted, 2 failed and 8 selected, a bar that added the failures would read as finished.
     */
    @Test
    fun deleteProgress_partialFailure_stillReportsProgressAndCurrentFile() {
        val progress = DeleteProgress(
            currentFile = "locked.txt",
            deletedFiles = 6,
            totalFiles = 8,
            failedFiles = 2
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(progress = progress, onCancel = {})
            }
        }
        composeTestRule.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.onNodeWithText(context.getString(R.string.delete_deleting)).assertIsDisplayed()
        composeTestRule.onNodeWithText("locked.txt").assertIsDisplayed()
        assertProgressFraction(0.75f)
        // The dialog must remain cancellable while failures accumulate.
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).assertIsDisplayed()
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

    /**
     * Asserts the delete dialog on screen reports [fraction] on its determinate bar.
     *
     * The heading, the current file and the cancel button all stay put when the indicator is fed a
     * constant, so both delete-progress tests here passed with `progress = { 0f }` — a bar that is
     * permanently empty for every delete. Counts are chosen so the expected fraction is exact.
     */
    private fun assertProgressFraction(fraction: Float) {
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertRangeInfoEquals(ProgressBarRangeInfo(fraction, 0f..1f))
    }

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    // endregion
}
