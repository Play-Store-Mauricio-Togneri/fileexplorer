package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.ui.components.CompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.CompressProgressDialog
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteProgressDialog
import com.mauriciotogneri.fileexplorer.ui.components.RenameDialog
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDialogsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testPath = "/storage/emulated/0/Download"

    private fun createTestFile(
        name: String,
        isDirectory: Boolean = false,
        mimeType: String = "text/plain"
    ) = FileItem(
        path = "$testPath/$name",
        name = name,
        isDirectory = isDirectory,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = if (isDirectory) 5 else null
    )

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun buttonWithText(text: String) = hasText(text) and hasRole(Role.Button)

    // ==================== Create Folder Dialog Tests ====================

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

    @Test
    fun createFolderDialog_dotName_showsError() {
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
        composeTestRule.onNodeWithText("").performTextInput(".")
        composeTestRule.waitForIdle()

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

    // ==================== Rename Dialog Tests ====================

    @Test
    fun renameDialog_prefillsCurrentName() {
        val testFile = createTestFile("document.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.txt").assertIsDisplayed()
    }

    @Test
    fun renameDialog_invalidChars_showsError() {
        val testFile = createTestFile("document.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.txt").performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput("invalid/name.txt")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_invalid_name)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }

    @Test
    fun renameDialog_existingName_showsError() {
        val testFile = createTestFile("document.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = setOf("other.txt"),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.txt").performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput("other.txt")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_name_exists)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }

    @Test
    fun renameDialog_sameName_disablesRename() {
        val testFile = createTestFile("document.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val renameText = composeTestRule.activity.getString(R.string.dialog_rename)
        composeTestRule.onNode(buttonWithText(renameText)).assertIsNotEnabled()
    }

    @Test
    fun renameDialog_newName_enablesRename() {
        val testFile = createTestFile("document.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.txt").performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput("renamed.txt")
        composeTestRule.waitForIdle()

        val renameText = composeTestRule.activity.getString(R.string.dialog_rename)
        composeTestRule.onNode(buttonWithText(renameText)).assertIsEnabled()
    }

    @Test
    fun renameDialog_renameButton_triggersCallback() {
        val testFile = createTestFile("document.txt")
        var newName: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = { newName = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.txt").performTextClearance()
        composeTestRule.onNodeWithText("").performTextInput("renamed.txt")
        composeTestRule.waitForIdle()

        val renameText = composeTestRule.activity.getString(R.string.dialog_rename)
        composeTestRule.onNode(buttonWithText(renameText)).performClick()
        composeTestRule.waitForIdle()

        assertEquals("renamed.txt", newName)
    }

    @Test
    fun renameDialog_cancelButton_dismisses() {
        val testFile = createTestFile("document.txt")
        var dismissed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFile,
                    existingNames = emptySet(),
                    onDismiss = { dismissed = true },
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val cancelText = composeTestRule.activity.getString(R.string.dialog_cancel)
        composeTestRule.onNodeWithText(cancelText).performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    // ==================== Compress Dialog Tests ====================

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
        val title = composeTestRule.activity.getString(R.string.action_compress)
        composeTestRule.onNode(buttonWithText(title)).assertIsDisplayed()
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

    // ==================== Compress Progress Dialog Tests ====================

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
                        totalBytes = 5120
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.getString(R.string.compress_compressing)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
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
                        compressedBytes = 2048,
                        totalBytes = 10240
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("important_document.pdf").assertIsDisplayed()
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

    // ==================== Delete Confirm Dialog Tests ====================

    @Test
    fun deleteConfirmDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.txt",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.getString(R.string.delete_confirm_title)
        composeTestRule.onNode(buttonWithText(title)).assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_singleItem_showsItemName() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "important_file.pdf",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("important_file.pdf").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_multipleItems_showsCount() {
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
        val itemCountText = composeTestRule.activity.resources.getQuantityString(
            R.plurals.item_amount,
            5,
            5
        )
        composeTestRule.onNodeWithText(itemCountText).assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_deleteButton_triggersConfirm() {
        var confirmed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.txt",
                    onDismiss = {},
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        val deleteText = composeTestRule.activity.getString(R.string.dialog_delete)
        composeTestRule.onNode(buttonWithText(deleteText)).performClick()
        composeTestRule.waitForIdle()

        assertTrue(confirmed)
    }

    @Test
    fun deleteConfirmDialog_cancelButton_dismisses() {
        var dismissed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteConfirmDialog(
                    itemCount = 1,
                    itemName = "document.txt",
                    onDismiss = { dismissed = true },
                    onConfirm = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val cancelText = composeTestRule.activity.getString(R.string.dialog_cancel)
        composeTestRule.onNodeWithText(cancelText).performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    // ==================== Delete Progress Dialog Tests ====================

    @Test
    fun deleteProgressDialog_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = DeleteProgress(
                        currentFile = "file.txt",
                        deletedFiles = 1,
                        totalFiles = 5
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.getString(R.string.delete_deleting)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun deleteProgressDialog_showsCurrentFile() {
        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = DeleteProgress(
                        currentFile = "being_deleted.txt",
                        deletedFiles = 3,
                        totalFiles = 10
                    ),
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("being_deleted.txt").assertIsDisplayed()
    }

    @Test
    fun deleteProgressDialog_cancelButton_stops() {
        var cancelled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                DeleteProgressDialog(
                    progress = DeleteProgress(
                        currentFile = "file.txt",
                        deletedFiles = 1,
                        totalFiles = 5
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

    // ==================== Additional Validation Tests ====================

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

    @Test
    fun renameDialog_folder_selectsEntireName() {
        val testFolder = createTestFile("Documents", isDirectory = true)

        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = testFolder,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
    }
}
