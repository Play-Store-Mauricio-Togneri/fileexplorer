package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    // No Selection Tests

    @Test
    fun actionBar_noSelection_doesNotRender() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(),
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Move to").assertDoesNotExist()
        composeTestRule.onNodeWithText("Copy to").assertDoesNotExist()
        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
    }

    // With Selection Tests

    @Test
    fun actionBar_withSelection_showsMoveCopyDeleteButtons() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun actionBar_singleSelection_showsRenameButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
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
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun actionBar_fileSelected_showsShareButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
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
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    fun actionBar_withSelection_showsCompressButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    onAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Compress").assertIsDisplayed()
    }

    // Button Click Tests

    @Test
    fun actionBar_moveToButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Move to").performClick()

        assertEquals(FileAction.MoveTo, receivedAction)
    }

    @Test
    fun actionBar_copyToButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Copy to").performClick()

        assertEquals(FileAction.CopyTo, receivedAction)
    }

    @Test
    fun actionBar_deleteButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
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
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Share").performClick()

        assertEquals(FileAction.Share, receivedAction)
    }

    @Test
    fun actionBar_compressButton_triggersAction() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ActionBar(
                    state = createState(selectedPaths = setOf(testFile.path)),
                    onAction = { receivedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Compress").performClick()

        assertEquals(FileAction.Compress, receivedAction)
    }
}
