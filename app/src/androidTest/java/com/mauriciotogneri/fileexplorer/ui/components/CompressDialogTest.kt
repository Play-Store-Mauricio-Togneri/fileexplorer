package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.testutil.buttonWithText
import com.mauriciotogneri.fileexplorer.testutil.hasRole
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Matches the dialog *heading* carrying [text], i.e. the node that is not the confirm button.
     *
     * `CompressDialog` uses `action_compress` for both, so [buttonWithText] — which every other
     * assertion in this file wants — matches the button whatever became of the heading.
     */
    private fun titleWithText(text: String) = hasText(text) and !hasRole(Role.Button)

    @Test
    fun compressDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // The heading, not the confirm button that repeats the same string: matching the button
        // here left the heading unreferenced by the whole suite, so deleting its Text passed.
        val title = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(titleWithText(title)).assertIsDisplayed()
    }

    @Test
    fun compressDialog_displaysZipSuffix() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(".zip").assertIsDisplayed()
    }

    @Test
    fun compressDialog_emptyName_disablesCompress() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val compressText = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(buttonWithText(compressText)).assertIsNotEnabled()
    }

    @Test
    fun compressDialog_validName_enablesCompress() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("archive")
        composeTestRule.waitForIdle()

        val compressText = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(buttonWithText(compressText)).assertIsEnabled()
    }

    @Test
    fun compressDialog_existingZipName_showsError() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = setOf("archive.zip"),
                    onDismiss = {},
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("archive")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.compress_error_file_exists)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }

    @Test
    fun compressDialog_doubleDotName_showsError() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("..")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_reserved_name)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()

        val compressText = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(buttonWithText(compressText)).assertIsNotEnabled()
    }

    @Test
    fun compressDialog_compressButton_triggersCallback() {
        var zipName: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = { zipName = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("myarchive")
        composeTestRule.waitForIdle()

        val compressText = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(buttonWithText(compressText)).performClick()
        composeTestRule.waitForIdle()

        assertEquals("myarchive.zip", zipName)
    }

    @Test
    fun compressDialog_cancelButton_dismisses() {
        var dismissed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = { dismissed = true },
                    onCompress = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val cancelText = composeTestRule.activity.getString(R.string.dialog_cancel)
        composeTestRule.onNodeWithText(cancelText).performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun compressDialog_inputWithZipExtension_normalizedCorrectly() {
        var zipName: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                CompressDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCompress = { zipName = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("archive.zip")
        composeTestRule.waitForIdle()

        val compressText = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(buttonWithText(compressText)).performClick()
        composeTestRule.waitForIdle()

        assertEquals("archive.zip", zipName)
    }
}
