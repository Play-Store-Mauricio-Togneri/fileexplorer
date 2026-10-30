package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressProgressDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Asserts the progress dialog on screen reports [fraction] on its determinate bar.
     *
     * The heading, the current file and the cancel button all stay put when the indicator is fed a
     * constant, so every progress-dialog test here passed with `progress = { 0f }` — a bar that is
     * permanently empty for every delete, compress and extract — until the value itself was read.
     *
     * Fixtures are sized so the expected fraction is exact in binary, and so that the file counts
     * and the byte counts yield different fractions: the compress and extract dialogs divide bytes,
     * and dividing files instead would otherwise go unnoticed.
     */
    private fun assertProgressFraction(fraction: Float) {
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertRangeInfoEquals(ProgressBarRangeInfo(fraction, 0f..1f))
    }

    @Test
    fun compressProgressDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressProgressDialog(
                    progress = CompressProgress(
                        currentFile = "file.txt",
                        compressedFiles = 1,
                        totalFiles = 5,
                        compressedBytes = 1024,
                        totalBytes = 4096
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.getString(R.string.compress_compressing)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        assertProgressFraction(0.25f)
    }

    @Test
    fun compressProgressDialog_showsCurrentFile() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressProgressDialog(
                    progress = CompressProgress(
                        currentFile = "important_document.pdf",
                        compressedFiles = 2,
                        totalFiles = 10,
                        compressedBytes = 3072,
                        totalBytes = 4096
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("important_document.pdf").assertIsDisplayed()
        assertProgressFraction(0.75f)
    }

    // Files the walk could not open never reach the archive, so a bar measured against the whole
    // selection (0.25 here) stops short of full on every compress that skipped one, saying it was
    // cut off when it finished everything it could. Files give 1 of 3 compressible, so dividing them
    // instead also fails.
    @Test
    fun compressProgressDialog_skippedFiles_measureAgainstCompressibleBytes() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressProgressDialog(
                    progress = CompressProgress(
                        currentFile = "file.txt",
                        compressedFiles = 1,
                        totalFiles = 4,
                        compressedBytes = 1024,
                        totalBytes = 4096,
                        skippedFiles = 1,
                        skippedBytes = 2048
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        assertProgressFraction(0.5f)
    }

    // A source that grew after the size tally can carry the skipped bytes past what is left, leaving
    // nothing compressible on paper while bytes are still being written. Without the floor at the
    // bytes already compressed there is nothing left to divide by, and the bar sits empty for the
    // rest of a run that is working.
    @Test
    fun compressProgressDialog_compressedBeyondCompressible_isFloored() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressProgressDialog(
                    progress = CompressProgress(
                        currentFile = "file.txt",
                        compressedFiles = 1,
                        totalFiles = 2,
                        compressedBytes = 1024,
                        totalBytes = 2048,
                        skippedFiles = 1,
                        skippedBytes = 2048
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        assertProgressFraction(1f)
    }

    @Test
    fun compressProgressDialog_cancelButton_triggersCancellation() {
        var cancelled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                CompressProgressDialog(
                    progress = CompressProgress(
                        currentFile = "file.txt",
                        compressedFiles = 1,
                        totalFiles = 5,
                        compressedBytes = 1024,
                        totalBytes = 5120
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
}
