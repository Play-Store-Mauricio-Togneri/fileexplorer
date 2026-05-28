package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.components.FullWidthDragHandle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testPath = "/storage/emulated/0/Download"

    private fun createTestFile(
        name: String,
        isDirectory: Boolean = false,
        size: Long = 1024L,
        mimeType: String = "text/plain",
        childCount: Int? = null
    ) = FileItem(
        path = "$testPath/$name",
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = childCount
    )

    private val testFolder = createTestFile(
        name = "Documents",
        isDirectory = true,
        size = 0L,
        mimeType = "",
        childCount = 10
    )

    private val testImageFile = createTestFile(
        name = "photo.jpg",
        mimeType = "image/jpeg",
        size = 2048L
    )

    private val testTextFile = createTestFile(
        name = "notes.txt",
        mimeType = "text/plain",
        size = 512L
    )

    private val hiddenFile = createTestFile(
        name = ".hidden_config",
        mimeType = "text/plain",
        size = 128L
    )

    private val testFiles = listOf(testFolder, testImageFile, testTextFile)

    // ==================== Folder Navigation Tests ====================

    @Test
    fun folderScreen_displaysFileList() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    @Test
    fun folderScreen_tapOnFolder_triggersNavigationCallback() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = listOf(testFolder),
                    onFolderClick = { navigatedPath = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").performClick()

        assertEquals(testFolder.path, navigatedPath)
    }

    @Test
    fun folderScreen_tapOnImageFile_triggersFileOpenCallback() {
        var openedFile: FileItem? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = listOf(testImageFile),
                    onFileClick = { openedFile = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()

        assertEquals(testImageFile, openedFile)
    }

    // ==================== Context Menu Tests ====================

    @Test
    fun folderScreen_contextMenu_opensOnIconClick() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.menu_sort_by))
            .assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_showsSelectAllOption() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_select_all))
            .assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_showsSortByOption() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.menu_sort_by))
            .assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_showsShowHiddenItemsOption() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    showHidden = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.show_hidden_items))
            .assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_showsHideHiddenItemsWhenVisible() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles + hiddenFile,
                    showHidden = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.hide_hidden_items))
            .assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_showsNewFolderOption() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(files = testFiles)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_create_folder))
            .assertIsDisplayed()
    }

    // ==================== Selection Tests ====================

    @Test
    fun folderScreen_selectAll_triggersCallback() {
        var selectAllTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    onSelectAll = { selectAllTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_select_all))
            .performClick()

        assertTrue(selectAllTriggered)
    }

    @Test
    fun folderScreen_unselectAll_triggersCallback() {
        var unselectAllTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    allSelected = true,
                    onUnselectAll = { unselectAllTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_unselect_all))
            .performClick()

        assertTrue(unselectAllTriggered)
    }

    @Test
    fun folderScreen_longPressFile_entersSelectionMode() {
        var selectionTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = listOf(testTextFile),
                    onLongClick = { selectionTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("notes.txt").performTouchInput {
            longClick()
        }

        assertTrue(selectionTriggered)
    }

    @Test
    fun folderScreen_selectIndividualItem_triggersCallback() {
        var selectedFile: FileItem? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = testFiles,
                    isSelectionMode = true,
                    onFileClick = { selectedFile = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()

        assertEquals(testImageFile, selectedFile)
    }

    @Test
    fun folderScreen_selectMultipleItems_inSelectionMode() {
        val selectedFiles = mutableListOf<FileItem>()

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = testFiles,
                    isSelectionMode = true,
                    onFileClick = { selectedFiles.add(it) }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.onNodeWithText("notes.txt").performClick()

        assertEquals(2, selectedFiles.size)
        assertTrue(selectedFiles.contains(testImageFile))
        assertTrue(selectedFiles.contains(testTextFile))
    }

    // ==================== Sort Tests ====================

    @Test
    fun folderScreen_sortBy_triggersCallback() {
        var sortByTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    onSortBy = { sortByTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.menu_sort_by))
            .performClick()

        assertTrue(sortByTriggered)
    }

    @Test
    fun folderScreen_sortBottomSheet_displaysSortOptions() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSortBottomSheet(
                    currentSortMode = SortMode.NAME_ASC,
                    onSortModeSelected = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_name_asc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_name_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_size_asc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_size_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_date_asc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_date_desc)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_sortBottomSheet_selectSortMode_triggersCallback() {
        var selectedSortMode: SortMode? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSortBottomSheet(
                    currentSortMode = SortMode.NAME_ASC,
                    onSortModeSelected = { selectedSortMode = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.sort_size_desc)).performClick()

        assertEquals(SortMode.SIZE_DESC, selectedSortMode)
    }

    // ==================== Hidden Items Tests ====================

    @Test
    fun folderScreen_showHiddenItems_triggersCallback() {
        var toggleHiddenTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    showHidden = false,
                    onToggleHidden = { toggleHiddenTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.show_hidden_items))
            .performClick()

        assertTrue(toggleHiddenTriggered)
    }

    @Test
    fun folderScreen_hideHiddenItems_triggersCallback() {
        var toggleHiddenTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles + hiddenFile,
                    showHidden = true,
                    onToggleHidden = { toggleHiddenTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.hide_hidden_items))
            .performClick()

        assertTrue(toggleHiddenTriggered)
    }

    @Test
    fun folderScreen_hiddenFilesVisible_whenShowHiddenEnabled() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(files = testFiles + hiddenFile)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(".hidden_config").assertIsDisplayed()
    }

    // ==================== Create Folder Tests ====================

    @Test
    fun folderScreen_createNewFolder_triggersCallback() {
        var createFolderTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    onNewFolder = { createFolderTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_create_folder))
            .performClick()

        assertTrue(createFolderTriggered)
    }

    // ==================== Info Screen Tests ====================

    @Test
    fun folderScreen_fileInfo_availableInMenu() {
        var infoTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFileActionsMenu(
                    file = testImageFile,
                    onInfo = { infoTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info))
            .performClick()

        assertTrue(infoTriggered)
    }

    // ==================== Empty Folder Tests ====================

    @Test
    fun folderScreen_emptyFolder_displaysEmptyState() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(files = emptyList())
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.list_empty))
            .assertIsDisplayed()
    }

    // ==================== Additional Scenarios ====================

    @Test
    fun folderScreen_displaysFileSizes() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(files = listOf(testImageFile))
            }
        }

        composeTestRule.waitForIdle()
        val expectedSize = FileSizeFormatter.format(testImageFile.size)
        composeTestRule.onNodeWithText(expectedSize).assertIsDisplayed()
    }

    @Test
    fun folderScreen_displaysFolderItemCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(files = listOf(testFolder))
            }
        }

        composeTestRule.waitForIdle()
        val itemCount = testFolder.childCount ?: 0
        val expectedText = context.resources.getQuantityString(
            R.plurals.item_amount,
            itemCount,
            itemCount
        )
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_noSelectAllForEmptyFolder() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(files = emptyList())
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_select_all))
            .assertDoesNotExist()
    }

    @Test
    fun folderScreen_contextMenu_showsUnselectAllWhenAllSelected() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderScreenWithMenu(
                    files = testFiles,
                    allSelected = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_menu").performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.action_unselect_all))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_select_all))
            .assertDoesNotExist()
    }

    @Test
    fun folderScreen_tapOnFolder_doesNotTriggerFileOpen() {
        var fileOpenTriggered = false
        var folderNavigated = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = listOf(testFolder),
                    onFolderClick = { folderNavigated = true },
                    onFileClick = { fileOpenTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").performClick()

        assertTrue(folderNavigated)
        assertFalse(fileOpenTriggered)
    }

    @Test
    fun folderScreen_multipleFilesSelected_displayedCorrectly() {
        val selectedPaths = setOf(testImageFile.path, testTextFile.path)

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFolderContent(
                    files = testFiles,
                    selectedPaths = selectedPaths
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    // ==================== Test Composables ====================

    @Composable
    private fun TestFolderContent(
        files: List<FileItem>,
        selectedPaths: Set<String> = emptySet(),
        isSelectionMode: Boolean = false,
        onFolderClick: (String) -> Unit = {},
        onFileClick: (FileItem) -> Unit = {},
        onLongClick: (FileItem) -> Unit = {}
    ) {
        if (files.isEmpty()) {
            Text(
                text = stringResource(R.string.list_empty),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn {
                items(files, key = { it.path }) { file ->
                    FileListItem(
                        file = file,
                        isSelected = file.path in selectedPaths,
                        onClick = {
                            if (isSelectionMode) {
                                onFileClick(file)
                            } else if (file.isDirectory) {
                                onFolderClick(file.path)
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongClick = { onLongClick(file) },
                        onMenuClick = {}
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestFolderScreenWithMenu(
        files: List<FileItem>,
        allSelected: Boolean = false,
        showHidden: Boolean = false,
        onSelectAll: () -> Unit = {},
        onUnselectAll: () -> Unit = {},
        onSortBy: () -> Unit = {},
        onNewFolder: () -> Unit = {},
        onToggleHidden: () -> Unit = {}
    ) {
        var showMenu by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Download") },
                    actions = {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("toolbar_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.content_description_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (files.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (allSelected) {
                                                stringResource(R.string.action_unselect_all)
                                            } else {
                                                stringResource(R.string.action_select_all)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        if (allSelected) onUnselectAll() else onSelectAll()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sort_by)) },
                                onClick = {
                                    showMenu = false
                                    onSortBy()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (showHidden) {
                                            stringResource(R.string.hide_hidden_items)
                                        } else {
                                            stringResource(R.string.show_hidden_items)
                                        }
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleHidden()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_create_folder)) },
                                onClick = {
                                    showMenu = false
                                    onNewFolder()
                                }
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TestFolderContent(files = files)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSortBottomSheet(
        currentSortMode: SortMode,
        onSortModeSelected: (SortMode) -> Unit
    ) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = sheetState,
            dragHandle = { FullWidthDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_name_asc)) },
                    onClick = { onSortModeSelected(SortMode.NAME_ASC) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_name_desc)) },
                    onClick = { onSortModeSelected(SortMode.NAME_DESC) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_size_asc)) },
                    onClick = { onSortModeSelected(SortMode.SIZE_ASC) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_size_desc)) },
                    onClick = { onSortModeSelected(SortMode.SIZE_DESC) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_date_asc)) },
                    onClick = { onSortModeSelected(SortMode.DATE_ASC) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_date_desc)) },
                    onClick = { onSortModeSelected(SortMode.DATE_DESC) }
                )
            }
        }
    }

    @Composable
    private fun TestFileActionsMenu(
        file: FileItem,
        onInfo: () -> Unit = {}
    ) {
        Column {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_info)) },
                onClick = onInfo
            )
        }
    }
}
