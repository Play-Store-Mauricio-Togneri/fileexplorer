package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.testutil.buttonWithText
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenameDialogTest {

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
    fun renameDialog_dotName_showsError() {
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
        composeTestRule.onNodeWithText("").performTextInput(".")
        composeTestRule.waitForIdle()

        val errorText = composeTestRule.activity.getString(R.string.error_reserved_name)
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()

        val renameText = composeTestRule.activity.getString(R.string.dialog_rename)
        composeTestRule.onNode(buttonWithText(renameText)).assertIsNotEnabled()
    }

    // The message alone is not the guard: a dialog that showed it but kept Rename enabled would
    // still let the user rename onto an existing entry.
    @Test
    fun renameDialog_existingName_showsErrorAndDisablesRename() {
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

        val renameText = composeTestRule.activity.getString(R.string.dialog_rename)
        composeTestRule.onNode(buttonWithText(renameText)).assertIsNotEnabled()
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

    /**
     * `RenameDialog` preselects the part of the name the user is most likely to replace, so the
     * first keystroke overwrites it. Which part depends on the item, and each of the four cases
     * below pins one branch of that expression:
     *
     * - a folder keeps its whole name selected, dots and all (`if (file.isDirectory)`);
     * - a file excludes its extension, so typing keeps `.jpg`;
     * - a file with several dots keeps only the last suffix, so `fileNameStem` splits at the last
     *   dot rather than the first;
     * - a dotfile keeps its whole name selected: a leading dot separates no extension, and only an
     *   interior one does.
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

    @Test
    fun renameDialog_dotfile_selectsEntireName() {
        val testFile = createTestFile(".gitignore")

        assertRenameSelection(testFile, TextRange(0, ".gitignore".length))
    }
}
