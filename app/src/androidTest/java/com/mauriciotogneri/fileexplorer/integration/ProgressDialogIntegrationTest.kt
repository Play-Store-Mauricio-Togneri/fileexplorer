package com.mauriciotogneri.fileexplorer.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.OperationProgress
import com.mauriciotogneri.fileexplorer.ui.components.OperationProgressDialog
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDialogIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun moveProgress_showsMovingTitle() {
        val progress = OperationProgress(
            mode = OperationMode.MOVE,
            currentFile = "test.txt",
            copiedBytes = 500L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Moving…").assertIsDisplayed()
    }

    @Test
    fun copyProgress_showsCopyingTitle() {
        val progress = OperationProgress(
            mode = OperationMode.COPY,
            currentFile = "test.txt",
            copiedBytes = 500L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Copying…").assertIsDisplayed()
    }

    @Test
    fun progressDialog_showsCurrentFileName() {
        val progress = OperationProgress(
            mode = OperationMode.COPY,
            currentFile = "important_document.pdf",
            copiedBytes = 100L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("important_document.pdf").assertIsDisplayed()
    }

    @Test
    fun progressDialog_showsCancelButton() {
        val progress = OperationProgress(
            mode = OperationMode.MOVE,
            currentFile = "file.txt",
            copiedBytes = 50L,
            totalBytes = 100L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsEnabled()
    }

    @Test
    fun progressDialog_cancelTriggersCallback() {
        var cancelled = false
        val progress = OperationProgress(
            mode = OperationMode.COPY,
            currentFile = "file.txt",
            copiedBytes = 50L,
            totalBytes = 100L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = { cancelled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue("Cancel callback should be triggered", cancelled)
    }

    @Test
    fun progressDialog_isCancelling_showsCancellingText() {
        val progress = OperationProgress(
            mode = OperationMode.MOVE,
            currentFile = "file.txt",
            copiedBytes = 50L,
            totalBytes = 100L,
            isCancelling = true
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancelling…").assertIsDisplayed()
    }

    @Test
    fun progressDialog_isCancelling_disablesCancelButton() {
        val progress = OperationProgress(
            mode = OperationMode.MOVE,
            currentFile = "file.txt",
            copiedBytes = 50L,
            totalBytes = 100L,
            isCancelling = true
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancelling…").assertIsNotEnabled()
    }

    @Test
    fun progressDialog_zeroProgress_showsZeroPercent() {
        val progress = OperationProgress(
            mode = OperationMode.COPY,
            currentFile = "starting.txt",
            copiedBytes = 0L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("starting.txt").assertIsDisplayed()
    }

    @Test
    fun progressDialog_fullProgress_showsFullProgress() {
        val progress = OperationProgress(
            mode = OperationMode.COPY,
            currentFile = "complete.txt",
            copiedBytes = 1000L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("complete.txt").assertIsDisplayed()
    }

    @Test
    fun progressDialog_longFileName_displaysWithEllipsis() {
        val longFileName = "this_is_a_very_long_file_name_that_should_be_truncated.txt"
        val progress = OperationProgress(
            mode = OperationMode.MOVE,
            currentFile = longFileName,
            copiedBytes = 500L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText(longFileName, substring = true).assertIsDisplayed()
    }

    @Test
    fun progressDialog_zeroTotalBytes_handlesGracefully() {
        val progress = OperationProgress(
            mode = OperationMode.COPY,
            currentFile = "empty.txt",
            copiedBytes = 0L,
            totalBytes = 0L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Copying…").assertIsDisplayed()
        composeTestRule.onNodeWithText("empty.txt").assertIsDisplayed()
    }

    @Test
    fun moveProgress_differentFileNames_updatesDisplay() {
        val currentProgress = OperationProgress(
            mode = OperationMode.MOVE,
            currentFile = "first.txt",
            copiedBytes = 100L,
            totalBytes = 1000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = currentProgress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("first.txt").assertIsDisplayed()
    }
}
