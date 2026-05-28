package com.mauriciotogneri.fileexplorer.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.ui.components.ApkPermissionDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.PasswordUncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressProgressDialog
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDialogsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ==================== Delete Confirm Dialog Tests ====================

    @Test
    fun deleteConfirmDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.pdf",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Title "Delete" appears twice (title + button), verify at least one exists
        composeTestRule.onAllNodesWithText(context.getString(R.string.delete_confirm_title))
            .fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun deleteConfirmDialog_displaysFileName() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "my_important_file.pdf",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("my_important_file.pdf").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_multipleItems_displaysItemCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 5,
                    itemName = null,
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("5 items").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_confirmButton_triggersCallback() {
        var confirmTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.pdf",
                    onDismiss = {},
                    onConfirm = { confirmTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        // Use matcher for button with "Delete" text (has click action)
        composeTestRule.onNode(
            hasText(context.getString(R.string.dialog_delete)) and hasClickAction()
        ).performClick()

        assertTrue(confirmTriggered)
    }

    @Test
    fun deleteConfirmDialog_cancelButton_triggersDismiss() {
        var dismissTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.pdf",
                    onDismiss = { dismissTriggered = true },
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).performClick()

        assertTrue(dismissTriggered)
    }

    @Test
    fun deleteConfirmDialog_displaysBothButtons() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.pdf",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).assertIsDisplayed()
        // Delete button (use matcher to find the clickable one)
        composeTestRule.onNode(
            hasText(context.getString(R.string.dialog_delete)) and hasClickAction()
        ).assertIsDisplayed()
    }

    // ==================== Uncompress Dialog Tests ====================

    @Test
    fun uncompressDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressDialog(
                    entryCount = 10,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_uncompress))
            .assertIsDisplayed()
    }

    @Test
    fun uncompressDialog_displaysEntryCount_singular() {
        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressDialog(
                    entryCount = 1,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 item will be extracted").assertIsDisplayed()
    }

    @Test
    fun uncompressDialog_displaysEntryCount_plural() {
        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressDialog(
                    entryCount = 15,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("15 items will be extracted").assertIsDisplayed()
    }

    @Test
    fun uncompressDialog_confirmButton_triggersExtract() {
        var extractTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressDialog(
                    entryCount = 10,
                    onDismiss = {},
                    onExtract = { extractTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_extract)).performClick()

        assertTrue(extractTriggered)
    }

    @Test
    fun uncompressDialog_cancelButton_triggersDismiss() {
        var dismissTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressDialog(
                    entryCount = 10,
                    onDismiss = { dismissTriggered = true },
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).performClick()

        assertTrue(dismissTriggered)
    }

    @Test
    fun uncompressDialog_zeroEntries_disablesExtractButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressDialog(
                    entryCount = 0,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_extract))
            .assertIsNotEnabled()
    }

    // ==================== Password Uncompress Dialog Tests ====================

    @Test
    fun passwordDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_password_title))
            .assertIsDisplayed()
    }

    @Test
    fun passwordDialog_displaysPasswordField() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_password_hint))
            .assertIsDisplayed()
    }

    @Test
    fun passwordDialog_displaysEntryCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 8,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("8 items will be extracted").assertIsDisplayed()
    }

    @Test
    fun passwordDialog_emptyPassword_disablesConfirm() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_extract))
            .assertIsNotEnabled()
    }

    @Test
    fun passwordDialog_withPassword_enablesConfirm() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_password_hint))
            .performTextInput("secret123")

        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_extract))
            .assertIsEnabled()
    }

    @Test
    fun passwordDialog_confirmButton_passesPassword() {
        var receivedPassword: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = { receivedPassword = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_password_hint))
            .performTextInput("mySecretPassword")

        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_extract)).performClick()

        assertEquals("mySecretPassword", receivedPassword)
    }

    @Test
    fun passwordDialog_cancelButton_triggersDismiss() {
        var dismissTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = { dismissTriggered = true },
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).performClick()

        assertTrue(dismissTriggered)
    }

    @Test
    fun passwordDialog_showPasswordButton_isDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.content_description_show_password)
        ).assertIsDisplayed()
    }

    @Test
    fun passwordDialog_togglePasswordVisibility_changesIcon() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PasswordUncompressDialog(
                    entryCount = 5,
                    onDismiss = {},
                    onExtract = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.content_description_show_password)
        ).performClick()

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.content_description_hide_password)
        ).assertIsDisplayed()
    }

    // ==================== Uncompress Progress Dialog Tests ====================

    @Test
    fun uncompressProgressDialog_displaysTitle() {
        val progress = UncompressProgress(
            currentFile = "document.txt",
            extractedFiles = 5,
            totalFiles = 10,
            extractedBytes = 5000L,
            totalBytes = 10000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_extracting))
            .assertIsDisplayed()
    }

    @Test
    fun uncompressProgressDialog_displaysCurrentFile() {
        val progress = UncompressProgress(
            currentFile = "photos/vacation.jpg",
            extractedFiles = 3,
            totalFiles = 10,
            extractedBytes = 3000L,
            totalBytes = 10000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photos/vacation.jpg").assertIsDisplayed()
    }

    @Test
    fun uncompressProgressDialog_cancelButton_triggersCallback() {
        var cancelTriggered = false
        val progress = UncompressProgress(
            currentFile = "document.txt",
            extractedFiles = 5,
            totalFiles = 10,
            extractedBytes = 5000L,
            totalBytes = 10000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressProgressDialog(
                    progress = progress,
                    onCancel = { cancelTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).performClick()

        assertTrue(cancelTriggered)
    }

    @Test
    fun uncompressProgressDialog_displaysCancelButton() {
        val progress = UncompressProgress(
            currentFile = "document.txt",
            extractedFiles = 5,
            totalFiles = 10,
            extractedBytes = 5000L,
            totalBytes = 10000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                UncompressProgressDialog(
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).assertIsDisplayed()
    }

    // ==================== APK Permission Dialog Tests ====================

    @Test
    fun apkPermissionDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ApkPermissionDialog(
                    source = "recent",
                    onDismiss = {},
                    onOpenSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.apk_permission_title))
            .assertIsDisplayed()
    }

    @Test
    fun apkPermissionDialog_displaysMessage() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ApkPermissionDialog(
                    source = "recent",
                    onDismiss = {},
                    onOpenSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.apk_permission_message))
            .assertIsDisplayed()
    }

    @Test
    fun apkPermissionDialog_settingsButton_triggersCallback() {
        var settingsTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ApkPermissionDialog(
                    source = "recent",
                    onDismiss = {},
                    onOpenSettings = { settingsTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.apk_permission_settings))
            .performClick()

        assertTrue(settingsTriggered)
    }

    @Test
    fun apkPermissionDialog_cancelButton_triggersDismiss() {
        var dismissTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ApkPermissionDialog(
                    source = "recent",
                    onDismiss = { dismissTriggered = true },
                    onOpenSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).performClick()

        assertTrue(dismissTriggered)
    }

    @Test
    fun apkPermissionDialog_displaysBothButtons() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ApkPermissionDialog(
                    source = "recent",
                    onDismiss = {},
                    onOpenSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.apk_permission_settings))
            .assertIsDisplayed()
    }
}
