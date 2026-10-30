package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteProgressDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Asserts the delete dialog on screen reports [fraction] on its determinate bar.
     *
     * The heading, the current file and the cancel button all stay put when the indicator is fed a
     * constant, so every delete-progress test here passed with `progress = { 0f }` — a bar that is
     * permanently empty for every delete. Counts are chosen so the expected fraction is exact.
     */
    private fun assertProgressFraction(fraction: Float) {
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertRangeInfoEquals(ProgressBarRangeInfo(fraction, 0f..1f))
    }

    @Test
    fun deleteProgressDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = DeleteProgress(
                        currentFile = "file.txt",
                        deletedFiles = 1,
                        totalFiles = 4
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.getString(R.string.delete_deleting)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        assertProgressFraction(0.25f)
    }

    @Test
    fun deleteProgressDialog_showsCurrentFile() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = DeleteProgress(
                        currentFile = "being_deleted.txt",
                        deletedFiles = 3,
                        totalFiles = 4
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("being_deleted.txt").assertIsDisplayed()
        assertProgressFraction(0.75f)
    }

    /**
     * The bar has to move as the delete walks the selection, which no single-frame assertion can
     * say: a dialog hardwired to one fraction satisfies each of those on its own. This one advances
     * the same composition, so an indicator that reads its progress once — or ignores it — fails.
     */
    @Test
    fun deleteProgressDialog_advancingProgress_movesIndicator() {
        var progress by mutableStateOf(
            DeleteProgress(currentFile = "first.txt", deletedFiles = 1, totalFiles = 4)
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(progress = progress, onCancel = {})
            }
        }

        composeTestRule.waitForIdle()
        assertProgressFraction(0.25f)

        composeTestRule.runOnIdle {
            progress = DeleteProgress(currentFile = "third.txt", deletedFiles = 3, totalFiles = 4)
        }
        composeTestRule.waitForIdle()

        assertProgressFraction(0.75f)
        composeTestRule.onNodeWithText("third.txt").assertIsDisplayed()
    }

    @Test
    fun deleteProgressDialog_cancelButton_stops() {
        var cancelled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = DeleteProgress(
                        currentFile = "file.txt",
                        deletedFiles = 1,
                        totalFiles = 5
                    ),
                    onCancel = { cancelled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        val cancelText = composeTestRule.activity.getString(R.string.dialog_cancel)
        composeTestRule.onNodeWithText(cancelText).performClick()
        composeTestRule.waitForIdle()

        assertTrue(cancelled)
    }

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
}
