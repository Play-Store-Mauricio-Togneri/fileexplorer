package com.mauriciotogneri.fileexplorer.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
import org.junit.Assert.assertEquals
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

    /** Filename-only checks once passed even when the bar ignored the operation's byte counts. */
    @Test
    fun progressChanges_updateTheIndicatorAndCurrentFile() {
        var progress by mutableStateOf(
            OperationProgress(OperationMode.COPY, "starting.txt", 0L, 1000L)
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(progress = progress, onCancel = {})
            }
        }

        assertProgressFraction(0f)
        composeTestRule.onNodeWithText("starting.txt").assertIsDisplayed()

        composeTestRule.runOnIdle {
            progress = progress.copy(currentFile = "middle.txt", copiedBytes = 250L)
        }
        assertProgressFraction(0.25f)
        composeTestRule.onNodeWithText("middle.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("starting.txt").assertDoesNotExist()

        composeTestRule.runOnIdle {
            progress = progress.copy(currentFile = "complete.txt", copiedBytes = 1000L)
        }
        assertProgressFraction(1f)
        composeTestRule.onNodeWithText("complete.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("middle.txt").assertDoesNotExist()
    }

    @Test
    fun zeroTotalBytes_showsZeroProgressAndCurrentFile() {
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(OperationMode.COPY, "empty.txt", 0L, 0L),
                    onCancel = {}
                )
            }
        }

        assertProgressFraction(0f)
        composeTestRule.onNodeWithText(string(R.string.progress_copying)).assertIsDisplayed()
        composeTestRule.onNodeWithText("empty.txt").assertIsDisplayed()
    }

    /** Semantics retain the full filename even when rendering clips or wraps instead of ellipsizing. */
    @Test
    fun longFileName_isEllipsizedOnOneLine() {
        val name = "long_filename_".repeat(100) + ".txt"
        composeTestRule.setContent {
            FileExplorerTheme {
                OperationProgressDialog(
                    progress = OperationProgress(OperationMode.MOVE, name, 500L, 1000L),
                    onCancel = {}
                )
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("Expected one filename text layout", 1, layouts.size)
        assertEquals("The filename must stay on one line", 1, layouts.single().lineCount)
        assertTrue("An overflowing filename must end in an ellipsis", layouts.single().isLineEllipsized(0))
    }

    private fun assertProgressFraction(fraction: Float) {
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertRangeInfoEquals(ProgressBarRangeInfo(fraction, 0f..1f))
    }
}
