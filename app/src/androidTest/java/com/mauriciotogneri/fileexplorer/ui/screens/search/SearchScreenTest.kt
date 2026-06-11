package com.mauriciotogneri.fileexplorer.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.components.FullWidthDragHandle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testFile = FileItem(
        path = "/storage/emulated/0/Documents/test.txt",
        name = "test.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "text/plain",
        childCount = null
    )

    private val testFolder = FileItem(
        path = "/storage/emulated/0/Documents/TestFolder",
        name = "TestFolder",
        isDirectory = true,
        size = 0L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "",
        childCount = 5
    )

    // ==================== Display Tests ====================

    @Test
    fun searchScreen_displaysSearchField() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "",
                    results = emptyList(),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.search_placeholder))
            .assertIsDisplayed()
    }

    @Test
    fun searchScreen_displaysBackButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "",
                    results = emptyList(),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .assertIsDisplayed()
    }

    // ==================== Text Input Tests ====================

    @Test
    fun searchScreen_typeText_updatesQuery() {
        var capturedQuery = ""

        composeTestRule.setContent {
            var query by remember { mutableStateOf("") }
            FileExplorerTheme {
                TestSearchScreen(
                    query = query,
                    results = emptyList(),
                    onQueryChange = {
                        query = it
                        capturedQuery = it
                    },
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.search_placeholder))
            .performTextInput("document")

        assertEquals("document", capturedQuery)
    }

    @Test
    fun searchScreen_withQuery_showsClearButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "some query",
                    results = emptyList(),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.search_clear))
            .assertIsDisplayed()
    }

    @Test
    fun searchScreen_emptyQuery_hidesClearButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "",
                    results = emptyList(),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.search_clear))
            .assertDoesNotExist()
    }

    @Test
    fun searchScreen_clearButtonClick_clearsQuery() {
        var clearClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "some query",
                    results = emptyList(),
                    onQueryChange = {},
                    onClearQuery = { clearClicked = true },
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.search_clear))
            .performClick()

        assertTrue("Clear should be triggered", clearClicked)
    }

    // ==================== Results Display Tests ====================

    @Test
    fun searchScreen_withResults_displaysFiles() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "test",
                    results = listOf(testFile),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt")
            .assertIsDisplayed()
    }

    @Test
    fun searchScreen_withMultipleResults_displaysAll() {
        val files = listOf(
            testFile,
            testFile.copy(path = "/storage/test2.txt", name = "document.pdf"),
            testFile.copy(path = "/storage/test3.txt", name = "photo.jpg")
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "test",
                    results = files,
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("document.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
    }

    // ==================== Contextual Menu Tests ====================

    @Test
    fun searchScreen_withResults_displaysFileInList() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "test",
                    results = listOf(testFile),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = {},
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Verify the file is displayed in the results list
        composeTestRule.onNodeWithText("test.txt")
            .assertIsDisplayed()
    }

    @Test
    fun searchScreen_bottomSheet_displaysFileActions() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFile,
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_with))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_folder))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_share))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info))
            .assertIsDisplayed()
    }

    @Test
    fun searchScreen_bottomSheet_forFolder_hidesOpenWithOpenFolderAndShare() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFolder,
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Folders should not show Open with, Open folder, or Share
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_with))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_folder))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.action_share))
            .assertDoesNotExist()
        // But should show Delete and Info
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info))
            .assertIsDisplayed()
    }

    @Test
    fun searchScreen_bottomSheet_openWithClick_triggersAction() {
        var actionTriggered: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFile,
                    onAction = { actionTriggered = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_with))
            .performClick()

        assertEquals("open_with", actionTriggered)
    }

    @Test
    fun searchScreen_bottomSheet_openFolderClick_triggersAction() {
        var actionTriggered: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFile,
                    onAction = { actionTriggered = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_folder))
            .performClick()

        assertEquals("open_folder", actionTriggered)
    }

    @Test
    fun searchScreen_bottomSheet_shareClick_triggersAction() {
        var actionTriggered: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFile,
                    onAction = { actionTriggered = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_share))
            .performClick()

        assertEquals("share", actionTriggered)
    }

    @Test
    fun searchScreen_bottomSheet_deleteClick_triggersAction() {
        var actionTriggered: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFile,
                    onAction = { actionTriggered = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete))
            .performClick()

        assertEquals("delete", actionTriggered)
    }

    @Test
    fun searchScreen_bottomSheet_infoClick_triggersAction() {
        var actionTriggered: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchFileActionsBottomSheet(
                    file = testFile,
                    onAction = { actionTriggered = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info))
            .performClick()

        assertEquals("info", actionTriggered)
    }

    // ==================== Navigation Tests ====================

    @Test
    fun searchScreen_backClick_triggersCallback() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchScreen(
                    query = "",
                    results = emptyList(),
                    onQueryChange = {},
                    onClearQuery = {},
                    onBackClick = { backClicked = true },
                    onFileClick = {},
                    onFileMenuClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        assertTrue("Back should be triggered", backClicked)
    }

    // ==================== Test Composables ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSearchScreen(
        query: String,
        results: List<FileItem>,
        onQueryChange: (String) -> Unit,
        onClearQuery: () -> Unit,
        onBackClick: () -> Unit,
        onFileClick: (FileItem) -> Unit,
        onFileMenuClick: (FileItem) -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_placeholder),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(x = (-12).dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    actions = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = onClearQuery) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = stringResource(R.string.search_clear)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = results,
                            key = { it.path }
                        ) { file ->
                            FileListItem(
                                file = file,
                                isSelected = false,
                                onClick = { onFileClick(file) },
                                onLongClick = { onFileMenuClick(file) },
                                onMenuClick = { onFileMenuClick(file) },
                                showMenu = true
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSearchFileActionsBottomSheet(
        file: FileItem,
        onAction: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { FullWidthDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                if (!file.isDirectory) {
                    SearchFileActionItem(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        text = stringResource(R.string.action_open_with),
                        onClick = { onAction("open_with") }
                    )

                    SearchFileActionItem(
                        icon = Icons.Outlined.Folder,
                        text = stringResource(R.string.action_open_folder),
                        onClick = { onAction("open_folder") }
                    )

                    SearchFileActionItem(
                        icon = Icons.Outlined.Share,
                        text = stringResource(R.string.action_share),
                        onClick = { onAction("share") }
                    )
                }

                SearchFileActionItem(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(R.string.action_delete),
                    onClick = { onAction("delete") }
                )

                SearchFileActionItem(
                    icon = Icons.Outlined.Info,
                    text = stringResource(R.string.action_info),
                    onClick = { onAction("info") }
                )
            }
        }
    }

    @Composable
    private fun SearchFileActionItem(
        icon: ImageVector,
        text: String,
        onClick: () -> Unit
    ) {
        DropdownMenuItem(
            text = { Text(text = text, style = MenuItemTextStyle) },
            onClick = onClick,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
