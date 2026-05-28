package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSelectionModeTest {

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
    private val testFile5 = createTestFile("video.mp4", mimeType = "video/mp4")
    private val testFiles = listOf(testFile1, testFile2, testFile3, testFolder, testFile5)

    // ==================== Enter Selection Mode Tests ====================

    @Test
    fun longPress_entersSelectionMode() {
        var isSelectionMode = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionModeChange = { isSelectionMode = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        assertFalse(isSelectionMode)

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }

        composeTestRule.waitForIdle()
        assertTrue(isSelectionMode)
    }

    @Test
    fun longPress_selectsItem() {
        var selectedPaths = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionChange = { selectedPaths = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }

        composeTestRule.waitForIdle()
        assertTrue(selectedPaths.contains(testFile1.path))
        assertEquals(1, selectedPaths.size)
    }

    @Test
    fun longPress_showsActionBar() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Move to").assertDoesNotExist()

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()
    }

    // ==================== Toggle Selection Tests ====================

    @Test
    fun selectionMode_tapTogglesSelection() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()

        composeTestRule.waitForIdle()
        val title = composeTestRule.activity.resources.getQuantityString(
            R.plurals.selection_count,
            2,
            2
        )
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun selectionMode_tapSelectedItem_deselects() {
        var selectedPaths = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionChange = { selectedPaths = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()

        assertTrue(selectedPaths.contains(testFile1.path))
        assertTrue(selectedPaths.contains(testFile2.path))

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectedPaths.contains(testFile1.path))
        assertTrue(selectedPaths.contains(testFile2.path))
    }

    @Test
    fun selectionMode_tapUnselectedItem_selects() {
        var selectedPaths = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionChange = { selectedPaths = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        assertFalse(selectedPaths.contains(testFile3.path))

        composeTestRule.onNodeWithText("notes.txt").performClick()
        composeTestRule.waitForIdle()

        assertTrue(selectedPaths.contains(testFile3.path))
    }

    @Test
    fun selectionMode_multipleSelection_works() {
        var selectedPaths = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionChange = { selectedPaths = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("notes.txt").performClick()
        composeTestRule.waitForIdle()

        assertEquals(3, selectedPaths.size)
        assertTrue(selectedPaths.contains(testFile1.path))
        assertTrue(selectedPaths.contains(testFile2.path))
        assertTrue(selectedPaths.contains(testFile3.path))
    }

    // ==================== Clear Selection Tests ====================

    @Test
    fun selectionMode_closeButton_clearsSelection() {
        var selectedPaths = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionChange = { selectedPaths = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        assertTrue(selectedPaths.isNotEmpty())

        val clearSelectionDescription = composeTestRule.activity.getString(
            R.string.content_description_clear_selection
        )
        composeTestRule.onNodeWithContentDescription(clearSelectionDescription).performClick()
        composeTestRule.waitForIdle()

        assertTrue(selectedPaths.isEmpty())
    }

    @Test
    fun selectionMode_closeButton_hidesActionBar() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()

        val clearSelectionDescription = composeTestRule.activity.getString(
            R.string.content_description_clear_selection
        )
        composeTestRule.onNodeWithContentDescription(clearSelectionDescription).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Move to").assertDoesNotExist()
    }

    // ==================== Select All Tests ====================

    @Test
    fun selectionMode_selectAll_selectsAllItems() {
        var selectedPaths = emptySet<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionChange = { selectedPaths = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        assertEquals(1, selectedPaths.size)

        val selectAllDescription = composeTestRule.activity.getString(R.string.action_select_all)
        composeTestRule.onNodeWithContentDescription(selectAllDescription).performClick()
        composeTestRule.waitForIdle()

        assertEquals(testFiles.size, selectedPaths.size)
        testFiles.forEach { file ->
            assertTrue(selectedPaths.contains(file.path))
        }
    }

    @Test
    fun selectionMode_selectAllIcon_showsWhenNotAllSelected() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()

        val selectAllDescription = composeTestRule.activity.getString(R.string.action_select_all)
        composeTestRule.onNodeWithContentDescription(selectAllDescription).assertIsDisplayed()
    }

    @Test
    fun selectionMode_selectAllIcon_hiddenWhenAllSelected() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        val selectAllDescription = composeTestRule.activity.getString(R.string.action_select_all)
        composeTestRule.onNodeWithContentDescription(selectAllDescription).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(selectAllDescription).assertDoesNotExist()
    }

    // ==================== Title Count Tests ====================

    @Test
    fun selectionMode_titleShowsCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("notes.txt").performClick()
        composeTestRule.waitForIdle()

        val expectedTitle = composeTestRule.activity.resources.getQuantityString(
            R.plurals.selection_count,
            3,
            3
        )
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun selectionMode_titleShowsCount_singular() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        val expectedTitle = composeTestRule.activity.resources.getQuantityString(
            R.plurals.selection_count,
            1,
            1
        )
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    // ==================== Auto-exit Selection Mode Tests ====================

    @Test
    fun selectionMode_lastItemDeselected_exitsMode() {
        var isSelectionMode = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(
                    files = testFiles,
                    onSelectionModeChange = { isSelectionMode = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        assertTrue(isSelectionMode)

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        assertFalse(isSelectionMode)
    }

    @Test
    fun selectionMode_lastItemDeselected_hidesActionBar() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSelectionModeScreen(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Move to").assertDoesNotExist()
    }

    // ==================== Test Composables ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSelectionModeScreen(
        files: List<FileItem>,
        onSelectionChange: (Set<String>) -> Unit = {},
        onSelectionModeChange: (Boolean) -> Unit = {}
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
            topBar = {
                if (isSelectionMode) {
                    SelectionTopAppBar(
                        selectedCount = selectedPaths.size,
                        allSelected = selectedPaths.size == files.size,
                        onClearSelection = {
                            selectedPaths = emptySet()
                            onSelectionChange(emptySet())
                            onSelectionModeChange(false)
                        },
                        onSelectAll = {
                            selectedPaths = files.map { it.path }.toSet()
                            onSelectionChange(selectedPaths)
                        }
                    )
                }
            },
            bottomBar = {
                ActionBar(
                    state = state,
                    onAction = {}
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
                                    val newSelected = if (file.path in selectedPaths) {
                                        selectedPaths - file.path
                                    } else {
                                        selectedPaths + file.path
                                    }
                                    selectedPaths = newSelected
                                    onSelectionChange(newSelected)
                                    onSelectionModeChange(newSelected.isNotEmpty())
                                }
                            },
                            onLongClick = {
                                val newSelected = if (file.path in selectedPaths) {
                                    selectedPaths - file.path
                                } else {
                                    selectedPaths + file.path
                                }
                                selectedPaths = newSelected
                                onSelectionChange(newSelected)
                                onSelectionModeChange(newSelected.isNotEmpty())
                            },
                            onMenuClick = {}
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SelectionTopAppBar(
        selectedCount: Int,
        allSelected: Boolean,
        onClearSelection: () -> Unit,
        onSelectAll: () -> Unit
    ) {
        TopAppBar(
            title = {
                Text(
                    text = pluralStringResource(
                        R.plurals.selection_count,
                        selectedCount,
                        selectedCount
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(
                            R.string.content_description_clear_selection
                        )
                    )
                }
            },
            actions = {
                if (!allSelected) {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = stringResource(
                                R.string.action_select_all
                            )
                        )
                    }
                }
            }
        )
    }
}
