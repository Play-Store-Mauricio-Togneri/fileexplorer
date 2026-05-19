package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.OperationProgress
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationProgressDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun moveMode_showsMovingTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Moving…").assertIsDisplayed()
    }

    @Test
    fun copyMode_showsCopyingTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.COPY,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Copying…").assertIsDisplayed()
    }

    @Test
    fun currentFileName_isDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "important_document.pdf",
                        copiedBytes = 500L,
                        totalBytes = 1000L
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("important_document.pdf").assertIsDisplayed()
    }

    @Test
    fun cancelButton_isDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun cancelButton_isEnabled_whenNotCancelling() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L,
                        isCancelling = false
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsEnabled()
    }

    @Test
    fun cancelButton_isDisabled_whenCancelling() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L,
                        isCancelling = true
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancelling…").assertIsNotEnabled()
    }

    @Test
    fun cancelling_showsCancellingText() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L,
                        isCancelling = true
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cancelling…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun cancelButton_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(
                        mode = OperationMode.MOVE,
                        currentFile = "file.txt",
                        copiedBytes = 500L,
                        totalBytes = 1000L
                    ),
                    onCancel = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(clicked)
    }
}
