package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionModeIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testPath = "/storage/emulated/0/Download"

    private fun createTestFile(
        name: String,
        isDirectory: Boolean = false,
        size: Long = 1024L,
        mimeType: String = "text/plain"
    ) = FileItem(
        path = "$testPath/$name",
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = if (isDirectory) 5 else null
    )

    private val testFile1 = createTestFile("document.pdf", mimeType = "application/pdf")
    private val testFile2 = createTestFile("photo.jpg", mimeType = "image/jpeg")
    private val testFile3 = createTestFile("notes.txt")
    private val testFolder = createTestFile("Documents", isDirectory = true)
    private val testFiles = listOf(testFile1, testFile2, testFile3, testFolder)

    // ==================== Delete Multiple Tests ====================

    @Test
    fun selectionMode_deleteMultiple_triggersDeleteAction() {
        var receivedAction: FileAction? = null
        var selectedAtAction = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(
                    files = testFiles,
                    onAction = { action, selected ->
                        receivedAction = action
                        selectedAtAction = selected
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("notes.txt").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileAction.Delete, receivedAction)
        assertEquals(3, selectedAtAction.size)
        assertTrue(selectedAtAction.contains(testFile1.path))
        assertTrue(selectedAtAction.contains(testFile2.path))
        assertTrue(selectedAtAction.contains(testFile3.path))
    }

    // ==================== Move Multiple Tests ====================

    @Test
    fun selectionMode_moveMultiple_triggersMoveTo() {
        var receivedAction: FileAction? = null
        var selectedAtAction = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(
                    files = testFiles,
                    onAction = { action, selected ->
                        receivedAction = action
                        selectedAtAction = selected
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Move to").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileAction.MoveTo, receivedAction)
        assertEquals(2, selectedAtAction.size)
        assertTrue(selectedAtAction.contains(testFile1.path))
        assertTrue(selectedAtAction.contains(testFile2.path))
    }

    // ==================== Copy Multiple Tests ====================

    @Test
    fun selectionMode_copyMultiple_triggersCopyTo() {
        var receivedAction: FileAction? = null
        var selectedAtAction = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(
                    files = testFiles,
                    onAction = { action, selected ->
                        receivedAction = action
                        selectedAtAction = selected
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("notes.txt").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Copy to").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileAction.CopyTo, receivedAction)
        assertEquals(3, selectedAtAction.size)
    }

    // ==================== Compress Multiple Tests ====================

    @Test
    fun selectionMode_compressMultiple_triggersCompress() {
        var receivedAction: FileAction? = null
        var selectedAtAction = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(
                    files = testFiles,
                    onAction = { action, selected ->
                        receivedAction = action
                        selectedAtAction = selected
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Compress").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileAction.Compress, receivedAction)
        assertEquals(3, selectedAtAction.size)
        assertTrue(selectedAtAction.contains(testFile1.path))
        assertTrue(selectedAtAction.contains(testFile2.path))
        assertTrue(selectedAtAction.contains(testFolder.path))
    }

    // ==================== Share Multiple Tests ====================

    @Test
    fun selectionMode_shareMultipleFiles_triggersShare() {
        var receivedAction: FileAction? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(
                    files = testFiles,
                    onAction = { action, _ -> receivedAction = action }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Share").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileAction.Share, receivedAction)
    }

    @Test
    fun selectionMode_shareNotShownForFolders() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    fun selectionMode_shareNotShownWhenMixedSelection() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
    }

    // ==================== Rename Tests ====================

    @Test
    fun selectionMode_renameOnlyForSingleSelection() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Rename").assertIsDisplayed()

        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Rename").assertDoesNotExist()
    }

    // ==================== Action Clears Selection Tests ====================

    @Test
    fun selectionMode_actionTriggered_selectionStillAvailable() {
        var selectedAtAction = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeIntegrationScreen(
                    files = testFiles,
                    onAction = { _, selected -> selectedAtAction = selected }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, selectedAtAction.size)
    }

    // ==================== Test Composables ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSelectionModeIntegrationScreen(
        files: List<FileItem>,
        onAction: (FileAction, Set<String>) -> Unit = { _, _ -> }
    ) {
        var selectedPaths by remember { mutableStateOf(emptySet<String>()) }

        val state = FolderUiState(
            currentPath = testPath,
            files = files,
            selectedPaths = selectedPaths,
            isLoading = false
        )

        val isSelectionMode = selectedPaths.isNotEmpty()

        Scaffold(
            bottomBar = {
                ActionBar(
                    state = state,
                    onAction = { action ->
                        onAction(action, selectedPaths)
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn {
                    items(files, key = { it.path }) { file ->
                        FileListItem(
                            file = file,
                            isSelected = file.path in selectedPaths,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedPaths = if (file.path in selectedPaths) {
                                        selectedPaths - file.path
                                    } else {
                                        selectedPaths + file.path
                                    }
                                }
                            },
                            onLongClick = {
                                selectedPaths = if (file.path in selectedPaths) {
                                    selectedPaths - file.path
                                } else {
                                    selectedPaths + file.path
                                }
                            },
                            onMenuClick = {}
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
