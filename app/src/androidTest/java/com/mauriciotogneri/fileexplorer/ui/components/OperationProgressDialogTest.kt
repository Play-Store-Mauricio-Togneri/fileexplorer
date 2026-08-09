package com.mauriciotogneri.fileexplorer.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
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

    /** User-facing assertions go through resources so they hold in every supported locale. */
    private fun string(@StringRes id: Int): String = testContext.getString(id)

    private val testContext = InstrumentationRegistry.getInstrumentation().targetContext

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

        composeTestRule.onNodeWithText(string(R.string.progress_moving)).assertIsDisplayed()
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

        composeTestRule.onNodeWithText(string(R.string.progress_copying)).assertIsDisplayed()
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

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertIsDisplayed()
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

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertIsEnabled()
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

        composeTestRule.onNodeWithText(string(R.string.progress_cancelling)).assertIsNotEnabled()
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

        composeTestRule.onNodeWithText(string(R.string.progress_cancelling)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertDoesNotExist()
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

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertTrue(clicked)
    }
}
