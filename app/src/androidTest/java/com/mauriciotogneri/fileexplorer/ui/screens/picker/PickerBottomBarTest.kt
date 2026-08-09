package com.mauriciotogneri.fileexplorer.ui.screens.picker

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
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickerBottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** User-facing assertions go through resources so they hold in every supported locale. */
    private fun string(@StringRes id: Int): String = testContext.getString(id)

    private val testContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun moveMode_showsMoveHereButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_confirm_move)).assertIsDisplayed()
    }

    @Test
    fun copyMode_showsCopyHereButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.COPY,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_confirm_copy)).assertIsDisplayed()
    }

    @Test
    fun newFolderButton_isDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_new_folder)).assertIsDisplayed()
    }

    @Test
    fun validDestination_confirmButtonEnabled() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_confirm_move)).assertIsEnabled()
    }

    @Test
    fun invalidDestination_confirmButtonDisabled() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = false,
                    validationError = "Cannot move to the same folder",
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_confirm_move)).assertIsNotEnabled()
    }

    @Test
    fun validationError_isDisplayed() {
        val errorMessage = "Cannot move to the same folder"

        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = false,
                    validationError = errorMessage,
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun noValidationError_errorNotDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.validation_same_folder_move)).assertDoesNotExist()
    }

    @Test
    fun newFolderButton_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.MOVE,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = { clicked = true },
                    onConfirm = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_new_folder)).performClick()

        assertTrue(clicked)
    }

    @Test
    fun confirmButton_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                PickerBottomBar(
                    mode = OperationMode.COPY,
                    isValidDestination = true,
                    validationError = null,
                    onNewFolder = {},
                    onConfirm = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.picker_confirm_copy)).performClick()

        assertTrue(clicked)
    }
}
