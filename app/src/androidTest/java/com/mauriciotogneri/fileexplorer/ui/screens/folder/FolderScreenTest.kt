package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the real [FolderScreen] — list rendering, row taps, the toolbar overflow menu and the
 * sort sheet — over a temp directory backed by the real `FolderViewModel` and `FileRepository`.
 *
 * This file previously declared four private `@Composable` copies (`TestFolderContent`,
 * `TestFolderScreenWithMenu`, `TestSortBottomSheet`, `TestFileActionsMenu`) and asserted against
 * those. The menu copy in particular decided for itself which items to show, so the production
 * menu's own visibility rules were never run.
 */
@RunWith(AndroidJUnit4::class)
class FolderScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var testDir: File
    private lateinit var robot: FolderScreenRobot

    @Before
    fun setUp() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir = File(composeTestRule.activity.cacheDir, "test_folder_${System.currentTimeMillis()}")
            .apply { mkdirs() }
        robot = FolderScreenRobot(composeTestRule, testDir)
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir.deleteRecursively()
    }

    private fun string(id: Int) = robot.string(id)

    /** Documents/ (10 children), photo.jpg (2 KB), notes.txt (512 B). */
    private fun createStandardFixtures() {
        val documents = FileFixtures.createFolder(testDir, "Documents")
        repeat(10) { FileFixtures.createTextFile(documents, "child_$it.txt", "x") }
        FileFixtures.createTextFile(testDir, "photo.jpg", "p".repeat(2048))
        FileFixtures.createTextFile(testDir, "notes.txt", "n".repeat(512))
    }

    // ==================== List rendering ====================

    @Test
    fun folderScreen_displaysFileList() {
        createStandardFixtures()
        robot.render()

        robot.waitForText("Documents")
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    @Test
    fun folderScreen_displaysFileSizes() {
        FileFixtures.createTextFile(testDir, "photo.jpg", "p".repeat(2048))
        robot.render()

        robot.waitForText("photo.jpg")
        composeTestRule.onNodeWithText(FileSizeFormatter.format(2048L)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_displaysFolderItemCount() {
        val documents = FileFixtures.createFolder(testDir, "Documents")
        repeat(3) { FileFixtures.createTextFile(documents, "child_$it.txt", "x") }
        robot.render()

        robot.waitForText("Documents")
        robot.waitForText(robot.plural(R.plurals.item_amount, 3))
        composeTestRule.onNodeWithText(robot.plural(R.plurals.item_amount, 3)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_emptyFolder_displaysEmptyState() {
        robot.render()

        robot.waitForText(string(R.string.list_empty))
        composeTestRule.onNodeWithText(string(R.string.list_empty)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_hiddenFiles_notShownByDefault() {
        FileFixtures.createTextFile(testDir, "visible.txt", "v")
        FileFixtures.createTextFile(testDir, ".hidden_config", "h")
        robot.render()

        robot.waitForText("visible.txt")
        composeTestRule.onNodeWithText(".hidden_config").assertDoesNotExist()
    }

    /**
     * The menu label flips with the current preference, so toggling it must both reveal the dotfile
     * and offer the inverse action.
     */
    @Test
    fun folderScreen_toggleHiddenItems_revealsDotfilesAndFlipsLabel() {
        FileFixtures.createTextFile(testDir, "visible.txt", "v")
        FileFixtures.createTextFile(testDir, ".hidden_config", "h")
        robot.render()
        robot.waitForText("visible.txt")

        robot.openOverflowMenu()
        composeTestRule.onNodeWithText(string(R.string.show_hidden_items)).assertIsDisplayed()
        robot.click(string(R.string.show_hidden_items))

        robot.waitForText(".hidden_config")
        composeTestRule.onNodeWithText(".hidden_config").assertIsDisplayed()

        robot.openOverflowMenu()
        composeTestRule.onNodeWithText(string(R.string.hide_hidden_items)).assertIsDisplayed()
        robot.click(string(R.string.hide_hidden_items))

        robot.waitForTextToDisappear(".hidden_config")
    }

    // ==================== Row taps ====================

    @Test
    fun folderScreen_tapOnFolder_navigatesToIt() {
        FileFixtures.createFolder(testDir, "Documents")
        var navigatedPath: String? = null
        robot.render(onNavigateToFolder = { navigatedPath = it })

        robot.waitForText("Documents")
        robot.click("Documents")

        assertEquals(File(testDir, "Documents").absolutePath, navigatedPath)
    }

    @Test
    fun folderScreen_tapOnFile_doesNotNavigate() {
        FileFixtures.createTextFile(testDir, "notes.txt", "n")
        var navigatedPath: String? = null
        robot.render(onNavigateToFolder = { navigatedPath = it })

        robot.waitForText("notes.txt")
        robot.click("notes.txt")

        assertNull("Tapping a file must not navigate into a folder", navigatedPath)
    }

    @Test
    fun folderScreen_backButton_triggersNavigateBack() {
        var backNavigated = false
        robot.render(onNavigateBack = { backNavigated = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Toolbar back should invoke onNavigateBack", backNavigated)
    }

    // ==================== Toolbar overflow menu ====================

    @Test
    fun folderScreen_contextMenu_showsCoreOptions() {
        createStandardFixtures()
        robot.render()
        robot.waitForText("notes.txt")

        robot.openOverflowMenu()

        composeTestRule.onNodeWithText(string(R.string.menu_sort_by)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_create_folder)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.show_hidden_items)).assertIsDisplayed()
    }

    /**
     * "Select all" is meaningless with nothing to select, so the production menu hides it. The old
     * replica made this decision itself, so this rule was never actually exercised.
     */
    @Test
    fun folderScreen_contextMenu_emptyFolder_hidesSelectAll() {
        robot.render()
        robot.waitForText(string(R.string.list_empty))

        robot.openOverflowMenu()

        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.action_create_folder)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_selectAll_selectsEveryRow() {
        createStandardFixtures()
        robot.render()
        robot.waitForText("notes.txt")

        robot.openOverflowMenu()
        robot.click(string(R.string.action_select_all))

        robot.waitForText(robot.plural(R.plurals.selection_count, 3))
        composeTestRule.onNodeWithText(robot.plural(R.plurals.selection_count, 3)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_contextMenu_whenAllSelected_offersUnselectAll() {
        createStandardFixtures()
        robot.render()
        robot.waitForText("notes.txt")
        robot.openOverflowMenu()
        robot.click(string(R.string.action_select_all))
        robot.waitForText(robot.plural(R.plurals.selection_count, 3))

        robot.openOverflowMenu()

        composeTestRule.onNodeWithText(string(R.string.action_unselect_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertDoesNotExist()
    }

    @Test
    fun folderScreen_unselectAll_clearsSelection() {
        createStandardFixtures()
        robot.render()
        robot.waitForText("notes.txt")
        robot.openOverflowMenu()
        robot.click(string(R.string.action_select_all))
        robot.waitForText(robot.plural(R.plurals.selection_count, 3))

        robot.openOverflowMenu()
        robot.click(string(R.string.action_unselect_all))

        robot.waitForTextToDisappear(robot.plural(R.plurals.selection_count, 3))
        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertDoesNotExist()
    }

    @Test
    fun folderScreen_createFolder_opensDialog() {
        robot.render()
        robot.waitForText(string(R.string.list_empty))

        robot.openOverflowMenu()
        robot.click(string(R.string.action_create_folder))

        composeTestRule.onNodeWithText(string(R.string.dialog_create)).assertIsDisplayed()
    }

    // ==================== Sort sheet ====================

    @Test
    fun folderScreen_sortSheet_displaysEverySortOption() {
        createStandardFixtures()
        robot.render()
        robot.waitForText("notes.txt")

        robot.openOverflowMenu()
        robot.click(string(R.string.menu_sort_by))

        composeTestRule.onNodeWithText(string(R.string.sort_name_asc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sort_name_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sort_size_asc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sort_size_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sort_date_asc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.sort_date_desc)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_sortSheet_selectingMode_appliesIt() {
        FileFixtures.createTextFile(testDir, "a.txt", "a")
        FileFixtures.createTextFile(testDir, "b.txt", "b")
        robot.render()
        robot.waitForText("b.txt")
        assertTrue("Default NAME_ASC order", robot.isTopToBottom("a.txt", "b.txt"))

        robot.openOverflowMenu()
        robot.click(string(R.string.menu_sort_by))
        robot.click(string(R.string.sort_name_desc))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { robot.isTopToBottom("b.txt", "a.txt") }.getOrDefault(false)
        }
        assertTrue("NAME_DESC should reverse the list", robot.isTopToBottom("b.txt", "a.txt"))
        assertEquals(SortMode.NAME_DESC, SortManager.sortMode.value)
    }

    // ==================== Row bottom sheet ====================

    @Test
    fun folderScreen_rowActions_offersInfo() {
        FileFixtures.createTextFile(testDir, "photo.jpg", "p")
        robot.render()

        robot.openRowActions("photo.jpg")

        composeTestRule.onNodeWithText(string(R.string.action_info)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_rowActions_forFile_offersShare() {
        FileFixtures.createTextFile(testDir, "photo.jpg", "p")
        robot.render()

        robot.openRowActions("photo.jpg")

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
    }

    /** Folders cannot be shared as a stream, so the sheet must omit the action for them. */
    @Test
    fun folderScreen_rowActions_forFolder_hidesShare() {
        FileFixtures.createFolder(testDir, "Documents")
        robot.render()

        robot.openRowActions("Documents")

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.action_info)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_rowActions_selectEntersSelectionMode() {
        createStandardFixtures()
        robot.render()

        robot.openRowActions("notes.txt")
        robot.click(string(R.string.action_select))

        robot.waitForText(robot.plural(R.plurals.selection_count, 1))
        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertIsDisplayed()
    }

    @Test
    fun folderScreen_beforeSelection_actionBarHidden() {
        createStandardFixtures()
        robot.render()
        robot.waitForText("notes.txt")

        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText(robot.plural(R.plurals.selection_count, 1)).assertDoesNotExist()
    }
}
