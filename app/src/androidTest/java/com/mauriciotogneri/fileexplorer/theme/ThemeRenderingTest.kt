package com.mauriciotogneri.fileexplorer.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.ActionBar
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.components.CreateFolderDialog
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.EmptyState
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import com.mauriciotogneri.fileexplorer.ui.theme.backgroundDark
import com.mauriciotogneri.fileexplorer.ui.theme.backgroundLight
import com.mauriciotogneri.fileexplorer.ui.theme.onSurfaceDark
import com.mauriciotogneri.fileexplorer.ui.theme.onSurfaceLight
import com.mauriciotogneri.fileexplorer.ui.theme.primaryDark
import com.mauriciotogneri.fileexplorer.ui.theme.primaryLight
import com.mauriciotogneri.fileexplorer.ui.theme.surfaceDark
import com.mauriciotogneri.fileexplorer.ui.theme.surfaceLight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeRenderingTest {

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

    // ==================== Light Theme Tests ====================

    @Test
    fun fileListItem_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                FileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt").assertIsDisplayed()
    }

    @Test
    fun fileListItem_lightTheme_selectedState_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                FileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt").assertIsDisplayed()
    }

    @Test
    fun folderListItem_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                FileListItem(
                    file = testFolder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("TestFolder").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun actionBar_lightTheme_rendersCorrectly() {
        val state = FolderUiState(
            currentPath = "/storage/emulated/0",
            files = listOf(testFile),
            selectedPaths = setOf(testFile.path),
            isLoading = false
        )

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                ActionBar(
                    state = state,
                    onAction = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_move_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_copy_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun createFolderDialog_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_create_folder))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                DeleteConfirmDialog(
                    itemCount = 3,
                    itemName = null,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(context.getString(R.string.delete_confirm_title))[0]
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .assertIsDisplayed()
    }

    // ==================== Dark Theme Tests ====================

    @Test
    fun fileListItem_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                FileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt").assertIsDisplayed()
    }

    @Test
    fun fileListItem_darkTheme_selectedState_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                FileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.txt").assertIsDisplayed()
    }

    @Test
    fun folderListItem_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                FileListItem(
                    file = testFolder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("TestFolder").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun actionBar_darkTheme_rendersCorrectly() {
        val state = FolderUiState(
            currentPath = "/storage/emulated/0",
            files = listOf(testFile),
            selectedPaths = setOf(testFile.path),
            isLoading = false
        )

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                ActionBar(
                    state = state,
                    onAction = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_move_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_copy_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun createFolderDialog_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                CreateFolderDialog(
                    existingNames = emptySet(),
                    onDismiss = {},
                    onCreate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_create_folder))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .assertIsDisplayed()
    }

    @Test
    fun deleteConfirmDialog_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                DeleteConfirmDialog(
                    itemCount = 3,
                    itemName = null,
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(context.getString(R.string.delete_confirm_title))[0]
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .assertIsDisplayed()
    }

    // ==================== Theme Color Verification Tests ====================

    @Test
    fun themeColors_lightTheme_providesCorrectColors() {
        var capturedPrimary: Color? = null
        var capturedBackground: Color? = null
        var capturedSurface: Color? = null
        var capturedOnSurface: Color? = null

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                capturedPrimary = MaterialTheme.colorScheme.primary
                capturedBackground = MaterialTheme.colorScheme.background
                capturedSurface = MaterialTheme.colorScheme.surface
                capturedOnSurface = MaterialTheme.colorScheme.onSurface
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        composeTestRule.waitForIdle()

        assertEquals("Light theme primary color mismatch", primaryLight, capturedPrimary)
        assertEquals("Light theme background color mismatch", backgroundLight, capturedBackground)
        assertEquals("Light theme surface color mismatch", surfaceLight, capturedSurface)
        assertEquals("Light theme onSurface color mismatch", onSurfaceLight, capturedOnSurface)
    }

    @Test
    fun themeColors_darkTheme_providesCorrectColors() {
        var capturedPrimary: Color? = null
        var capturedBackground: Color? = null
        var capturedSurface: Color? = null
        var capturedOnSurface: Color? = null

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                capturedPrimary = MaterialTheme.colorScheme.primary
                capturedBackground = MaterialTheme.colorScheme.background
                capturedSurface = MaterialTheme.colorScheme.surface
                capturedOnSurface = MaterialTheme.colorScheme.onSurface
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        composeTestRule.waitForIdle()

        assertEquals("Dark theme primary color mismatch", primaryDark, capturedPrimary)
        assertEquals("Dark theme background color mismatch", backgroundDark, capturedBackground)
        assertEquals("Dark theme surface color mismatch", surfaceDark, capturedSurface)
        assertEquals("Dark theme onSurface color mismatch", onSurfaceDark, capturedOnSurface)
    }

    // ==================== Theme Switching Tests ====================

    @Test
    fun themeSwitching_fromLightToDark_updatesColors() {
        var isDark by mutableStateOf(false)
        var capturedBackground: Color? = null

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                capturedBackground = MaterialTheme.colorScheme.background
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        composeTestRule.waitForIdle()
        assertEquals("Initial light theme background", backgroundLight, capturedBackground)

        isDark = true
        composeTestRule.waitForIdle()
        assertEquals("After switching to dark theme", backgroundDark, capturedBackground)
    }

    @Test
    fun themeSwitching_fromDarkToLight_updatesColors() {
        var isDark by mutableStateOf(true)
        var capturedBackground: Color? = null

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                capturedBackground = MaterialTheme.colorScheme.background
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        composeTestRule.waitForIdle()
        assertEquals("Initial dark theme background", backgroundDark, capturedBackground)

        isDark = false
        composeTestRule.waitForIdle()
        assertEquals("After switching to light theme", backgroundLight, capturedBackground)
    }

    // ==================== Empty State Theme Tests ====================

    @Test
    fun emptyState_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                EmptyState()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.list_empty)).assertIsDisplayed()
    }

    @Test
    fun emptyState_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                EmptyState()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.list_empty)).assertIsDisplayed()
    }
}
