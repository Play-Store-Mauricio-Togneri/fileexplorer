package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateFolderDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createFolderDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.getString(R.string.action_create_folder)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun createFolderDialog_emptyName_disablesCreate() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).assertIsNotEnabled()
    }

    @Test
    fun createFolderDialog_validName_enablesCreate() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("NewFolder")
        composeTestRule.waitForIdle()

        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).assertIsEnabled()
    }

    @Test
    fun createFolderDialog_invalidChars_showsError() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("invalid/name")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_invalid_name)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()

        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).assertIsNotEnabled()
    }

    /**
     * "." and ".." hold no invalid character, so the invalid-characters message never covered them:
     * the button greyed out with nothing saying why. Both names are checked, since each is its own
     * comparison in the dialog.
     */
    @Test
    fun createFolderDialog_dotName_showsError() {
        assertCreateFolderRefusesReservedName(".")
    }

    @Test
    fun createFolderDialog_doubleDotName_showsError() {
        assertCreateFolderRefusesReservedName("..")
    }

    private fun assertCreateFolderRefusesReservedName(name: String) {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput(name)
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_reserved_name)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()

        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).assertIsNotEnabled()
    }

    @Test
    fun createFolderDialog_existingName_showsError() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = setOf("ExistingFolder"),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("ExistingFolder")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_name_exists)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()

        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).assertIsNotEnabled()
    }

    @Test
    fun createFolderDialog_createButton_triggersCallback() {
        var createdName: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = { createdName = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("NewFolder")
        composeTestRule.waitForIdle()

        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).performClick()
        composeTestRule.waitForIdle()

        assertEquals("NewFolder", createdName)
    }

    @Test
    fun createFolderDialog_cancelButton_dismisses() {
        var dismissed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = { dismissed = true },
                    onCreate = {}
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
    fun createFolderDialog_trailingSpaces_trimmed() {
        var createdName: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = { createdName = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("  FolderName  ")
        composeTestRule.waitForIdle()

        val createText = composeTestRule.activity.getString(R.string.dialog_create)
        composeTestRule.onNodeWithText(createText).performClick()
        composeTestRule.waitForIdle()

        assertEquals("FolderName", createdName)
    }

    @Test
    fun createFolderDialog_backslash_showsError() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("").performTextInput("invalid\\name")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_invalid_name)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }
}
