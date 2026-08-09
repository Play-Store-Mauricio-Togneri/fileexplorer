package com.mauriciotogneri.fileexplorer.rtl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arabic and Urdu ship in `values-ar` / `values-ur`, so every row, bar and dialog has to mirror.
 *
 * Every assertion here must be one that LTR would fail. Seven of these tests used to assert only
 * that a piece of text was displayed inside a `LocalLayoutDirection provides Rtl` wrapper: deleting
 * the wrapper left them all green, so a component pinned to the left with `Arrangement.Absolute.*`
 * or `Modifier.absolutePadding` — the actual way mirroring breaks — passed every one of them.
 * Geometry via [getBoundsInRoot], or a click that only lands if hit testing mirrored too, is what
 * makes an RTL test an RTL test. Plain "does it render" belongs in the component's own test file.
 */
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

    /**
     * The row is a `Row` of leading icon, name, trailing overflow menu. Mirrored, the icon sits to
     * the right of the name and the menu to its left.
     *
     * The row's `combinedClickable` merges its descendants, so every query here needs
     * `useUnmergedTree = true`: on the merged tree the name and the menu both resolve to the single
     * row node and every bounds comparison collapses to `x < x`.
     */
    @Test
    fun fileListItem_rtl_overflowMenuTrailsToTheLeftOfTheName() {
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

        val nameBounds = composeTestRule
            .onNodeWithText("test.txt", useUnmergedTree = true)
            .getBoundsInRoot()
        val menuBounds = composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.content_description_more_options),
                useUnmergedTree = true
            )
            .getBoundsInRoot()

        assertTrue(
            "In RTL, the overflow menu should trail on the left of the name",
            menuBounds.left < nameBounds.left
        )
    }

    @Test
    fun folderListItem_rtl_iconLeadsOnTheRightOfTheName() {
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

        val nameBounds = composeTestRule
            .onNodeWithText("TestFolder", useUnmergedTree = true)
            .getBoundsInRoot()
        val iconBounds = composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.content_description_folder),
                useUnmergedTree = true
            )
            .getBoundsInRoot()

        assertTrue(
            "In RTL, the leading folder icon should sit on the right of the name",
            iconBounds.left > nameBounds.left
        )
    }

    // ==================== ActionBar RTL Tests ====================

    /**
     * `ActionBar` lays its buttons out in a `Row`, so the first declared action ends up rightmost
     * once mirrored. Move is declared before Copy.
     */
    @Test
    fun actionBar_rtl_actionsRunRightToLeft() {
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

        val moveBounds = composeTestRule
            .onNodeWithText(context.getString(R.string.action_move_to))
            .getBoundsInRoot()
        val copyBounds = composeTestRule
            .onNodeWithText(context.getString(R.string.action_copy_to))
            .getBoundsInRoot()

        assertTrue(
            "In RTL, Move should sit to the right of Copy",
            moveBounds.left > copyBounds.left
        )
    }

    /** Mirrored layout also has to mirror hit testing, or the buttons move but stop responding. */
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
}
