package com.mauriciotogneri.fileexplorer.rtl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtlLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testFile = FileItem(
        path = "/storage/emulated/0/test.txt",
        name = "test.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "text/plain",
        childCount = null
    )

    private val testFolder = FileItem(
        path = "/storage/emulated/0/TestFolder",
        name = "TestFolder",
        isDirectory = true,
        size = 0L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "",
        childCount = 5
    )

    // ==================== Breadcrumbs RTL Tests ====================

    @Test
    fun breadcrumbs_rtl_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Breadcrumbs(
                        currentPath = "/storage/emulated/0/Documents/Work",
                        onNavigateToPath = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_rtl_segmentsOrderedCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Breadcrumbs(
                        currentPath = "/storage/emulated/0/Documents/Work",
                        onNavigateToPath = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val documentsBounds = composeTestRule
            .onNodeWithText("Documents")
            .getBoundsInRoot()
        val workBounds = composeTestRule
            .onNodeWithText("Work")
            .getBoundsInRoot()

        assertTrue(
            "In RTL, earlier path segments should be on the right",
            documentsBounds.left > workBounds.left
        )
    }

    // ==================== FileListItem RTL Tests ====================

    @Test
    fun fileListItem_rtl_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FileListItem(
                        file = testFile,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt").assertIsDisplayed()
    }

    @Test
    fun folderListItem_rtl_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FileListItem(
                        file = testFolder,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("TestFolder").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.content_description_folder))
            .assertIsDisplayed()
    }

    @Test
    fun fileListItem_rtl_menuIconDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FileListItem(
                        file = testFile,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.content_description_more_options))
            .assertIsDisplayed()
    }

    // ==================== ActionBar RTL Tests ====================

    @Test
    fun actionBar_rtl_rendersCorrectly() {
        val state = FolderUiState(
            currentPath = "/storage/emulated/0",
            files = listOf(testFile),
            selectedPaths = setOf(testFile.path),
            isLoading = false
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ActionBar(
                        state = state,
                        onAction = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_move_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_copy_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun actionBar_rtl_buttonsStillClickable() {
        val state = FolderUiState(
            currentPath = "/storage/emulated/0",
            files = listOf(testFile),
            selectedPaths = setOf(testFile.path),
            isLoading = false
        )

        var actionTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ActionBar(
                        state = state,
                        onAction = { actionTriggered = true }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete))
            .performClick()

        assertTrue("Action should be triggered when button is clicked in RTL", actionTriggered)
    }

    // ==================== Dialog RTL Tests ====================

    @Test
    fun createFolderDialog_rtl_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CreateFolderDialog(
                        existingNames = emptySet(),
                        onDismiss = {},
                        onCreate = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_create_folder))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .assertIsDisplayed()
    }

    @Test
    fun createFolderDialog_rtl_buttonsOrderedCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CreateFolderDialog(
                        existingNames = emptySet(),
                        onDismiss = {},
                        onCreate = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val cancelBounds = composeTestRule
            .onNodeWithText(context.getString(R.string.dialog_cancel))
            .getBoundsInRoot()
        val createBounds = composeTestRule
            .onNodeWithText(context.getString(R.string.dialog_create))
            .getBoundsInRoot()

        assertTrue(
            "In RTL, Cancel button should be on the right (end) of Create button",
            cancelBounds.left > createBounds.left
        )
    }

    // ==================== EmptyState RTL Tests ====================

    @Test
    fun emptyState_rtl_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyState()
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.list_empty)).assertIsDisplayed()
    }
}
