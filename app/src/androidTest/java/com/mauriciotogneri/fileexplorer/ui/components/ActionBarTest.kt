package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.Clipboard
import com.mauriciotogneri.fileexplorer.data.model.ClipboardMode
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testPath = "/storage/emulated/0/Documents"

    private val testFolder = FileItem(
        path = "$testPath/Folder1",
        name = "Folder1",
        isDirectory = true,
        size = 0L,
        lastModified = 1000L,
        createdTime = 1000L,
        mimeType = "",
        childCount = 5
    )

    private val testFile = FileItem(
        path = "$testPath/file.txt",
        name = "file.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = 2000L,
        createdTime = 2000L,
        mimeType = "text/plain",
        childCount = null
    )

    private val testFiles = listOf(testFolder, testFile)

    private fun createState(
        selectedPaths: Set<String> = emptySet()
    ) = FolderUiState(
        currentPath = testPath,
        files = testFiles,
        selectedPaths = selectedPaths,
        isLoading = false
    )

    private val emptyClipboard = Clipboard()

    private val clipboardWithItems = Clipboard(
        items = listOf(testFile),
        mode = ClipboardMode.COPY,
        sourceParent = "/storage/emulated/0/Downloads"
    )

    // No Selection Tests

    @Test
    fun actionBar_noSelection_showsCreateFolderButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
    }

    @Test
    fun actionBar_noSelection_emptyClipboard_hidesPasteButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Paste").assertDoesNotExist()
    }

    @Test
    fun actionBar_noSelection_withClipboard_showsPasteButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = clipboardWithItems,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Paste").assertIsDisplayed()
    }

    @Test
    fun actionBar_noSelection_hidesCutCopyDeleteButtons() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cut").assertDoesNotExist()
        composeTestRule.onNodeWithText("Copy").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    // With Selection Tests

    @Test
    fun actionBar_withSelection_showsCutCopyDeleteButtons() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Cut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun actionBar_withSelection_hidesCreateFolderButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("New folder").assertDoesNotExist()
    }

    @Test
    fun actionBar_singleSelection_showsRenameButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Rename").assertIsDisplayed()
    }

    @Test
    fun actionBar_multipleSelection_hidesRenameButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path, testFolder.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun actionBar_partialSelection_showsSelectAllButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Select all").assertIsDisplayed()
    }

    @Test
    fun actionBar_allSelected_hidesSelectAllButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path, testFolder.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Select all").assertDoesNotExist()
    }

    @Test
    fun actionBar_fileSelected_showsShareButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
    }

    @Test
    fun actionBar_onlyFolderSelected_hidesShareButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFolder.path)),
                    clipboard = emptyClipboard,
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
    }

    // Button Click Tests

    @Test
    fun actionBar_cutButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Cut").performClick()

        assertEquals(FileAction.Cut, receivedAction)
    }

    @Test
    fun actionBar_copyButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Copy").performClick()

        assertEquals(FileAction.Copy, receivedAction)
    }

    @Test
    fun actionBar_pasteButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = clipboardWithItems,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Paste").performClick()

        assertEquals(FileAction.Paste, receivedAction)
    }

    @Test
    fun actionBar_deleteButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assertEquals(FileAction.Delete, receivedAction)
    }

    @Test
    fun actionBar_renameButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Rename").performClick()

        assertEquals(FileAction.Rename, receivedAction)
    }

    @Test
    fun actionBar_shareButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Share").performClick()

        assertEquals(FileAction.Share, receivedAction)
    }

    @Test
    fun actionBar_selectAllButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Select all").performClick()

        assertEquals(FileAction.SelectAll, receivedAction)
    }

    @Test
    fun actionBar_createFolderButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = emptyClipboard,
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("New folder").performClick()

        assertEquals(FileAction.CreateFolder, receivedAction)
    }

    // Paste Visibility Edge Cases

    @Test
    fun actionBar_pasteIntoSameFolder_hidesPasteButton() {
        val clipboardFromSameFolder = Clipboard(
            items = listOf(testFile),
            mode = ClipboardMode.COPY,
            sourceParent = testPath // Same as current path
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    clipboard = clipboardFromSameFolder,
                    onAction = {}
                )
            }
        }

        // Paste should not be visible because we can't paste into the same folder we copied from
        composeTestRule.onNodeWithText("Paste").assertDoesNotExist()
    }
}
