package com.mauriciotogneri.fileexplorer.ui.screens.home

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
        // "Delete" is both the dialog title and its confirm button, so match the title by
        // excluding the one carrying a click action rather than by index.
        composeTestRule.onNode(
            hasText(context.getString(R.string.delete_confirm_title)) and hasClickAction().not()
        ).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 5, 5)
        ).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.uncompress_confirm, 1, 1)
        ).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.uncompress_confirm, 15, 15)
        ).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.uncompress_confirm, 8, 8)
        ).assertIsDisplayed()
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

    /**
     * The password field's *visible* text.
     *
     * `CoreTextField` publishes the text after the visual transformation as `EditableText` (the
     * untransformed value goes to `InputText`), so this is the string the user actually sees.
     */
    private fun visiblePasswordText(): String =
        composeTestRule.onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    /**
     * The eye swapping its icon is not the feature. With the field's `visualTransformation` left at
     * `PasswordVisualTransformation()` unconditionally the icon still toggles forever while the
     * password is never revealed, and asserting only the content description passed — so what the
     * field displays is read on both sides of the toggle.
     */
    @Test
    fun passwordDialog_togglePasswordVisibility_revealsPasswordAndChangesIcon() {
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
        val typed = "correct horse battery"
        composeTestRule.onNodeWithText(context.getString(R.string.uncompress_password_hint))
            .performTextInput(typed)
        composeTestRule.waitForIdle()

        val masked = PasswordVisualTransformation().mask.toString().repeat(typed.length)
        assertEquals("Password must be masked before the toggle", masked, visiblePasswordText())

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.content_description_show_password)
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals("Password must be revealed after the toggle", typed, visiblePasswordText())
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.content_description_hide_password)
        ).assertIsDisplayed()
    }

    // ==================== Uncompress Progress Dialog Tests ====================

    /**
     * Asserts the extract dialog on screen reports [fraction] on its determinate bar.
     *
     * The heading, the current file and the cancel button all stay put when the indicator is fed a
     * constant, so both tests below passed with `progress = { 0f }` — a bar that is permanently
     * empty for every extraction. The dialog divides *bytes*, so the byte counts are sized to an
     * exact fraction that the file counts do not also produce.
     */
    private fun assertProgressFraction(fraction: Float) {
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertRangeInfoEquals(ProgressBarRangeInfo(fraction, 0f..1f))
    }

    @Test
    fun uncompressProgressDialog_displaysTitle() {
        val progress = UncompressProgress(
            currentFile = "document.txt",
            extractedFiles = 5,
            totalFiles = 10,
            extractedBytes = 1024L,
            totalBytes = 4096L
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
        assertProgressFraction(0.25f)
    }

    @Test
    fun uncompressProgressDialog_displaysCurrentFile() {
        val progress = UncompressProgress(
            currentFile = "photos/vacation.jpg",
            extractedFiles = 3,
            totalFiles = 10,
            extractedBytes = 3072L,
            totalBytes = 4096L
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
        assertProgressFraction(0.75f)
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
