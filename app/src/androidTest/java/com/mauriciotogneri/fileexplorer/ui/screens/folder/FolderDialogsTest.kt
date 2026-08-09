package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
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

    // DeleteConfirmDialog is a shared ui/components dialog, not a folder-specific one, and every
    // case that was here — title, single item name, multi-item count, confirm, cancel — is covered
    // by HomeDialogsTest. Duplicating it paid for the same five scenarios on every emulator run.

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

    /**
     * `RenameDialog` preselects the part of the name the user is most likely to replace, so the
     * first keystroke overwrites it. Which part depends on the item, and each of the three cases
     * below pins one branch of that expression:
     *
     * - a folder keeps its whole name selected, dots and all (`if (file.isDirectory)`);
     * - a file excludes its extension, so typing keeps `.jpg`;
     * - a file with several dots keeps only the last suffix (`substringBeforeLast`, not
     *   `substringBefore`).
     *
     * All three assert [SemanticsProperties.TextSelectionRange]. Asserting that the name is merely
     * displayed — which is what the folder case used to do — passes with the selection dropped
     * entirely, and the folder fixture is deliberately dotted so the `isDirectory` branch cannot be
     * removed without a failure.
     */
    private fun assertRenameSelection(file: FileItem, expected: TextRange) {
        composeTestRule.setContent {
            FileExplorerTheme {
                RenameDialog(
                    file = file,
                    existingNames = emptySet(),
                    onDismiss = {},
                    onRename = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(file.name).assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, expected)
        )
    }

    @Test
    fun renameDialog_folder_selectsEntireName() {
        val testFolder = createTestFile("Backup.2024", isDirectory = true)

        assertRenameSelection(testFolder, TextRange(0, "Backup.2024".length))
    }

    @Test
    fun renameDialog_file_selectsBaseNameWithoutExtension() {
        val testFile = createTestFile("photo.jpg")

        assertRenameSelection(testFile, TextRange(0, "photo".length))
    }

    @Test
    fun renameDialog_fileWithSeveralDots_selectsEverythingBeforeTheLastOne() {
        val testFile = createTestFile("archive.tar.gz")

        assertRenameSelection(testFile, TextRange(0, "archive.tar".length))
    }
}
