package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Selection mode driven through the real [FolderScreen], so the assertions land on
 * `FolderViewModel.toggleSelection` / `selectAll` / `clearSelection` rather than on a copy.
 *
 * The previous version re-implemented `selectedPaths ± path` inside a private test composable, which
 * meant the production selection state machine — including its auto-exit when the last item is
 * deselected — had no UI coverage at all.
 */
@RunWith(AndroidJUnit4::class)
class FolderSelectionModeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var testDir: File
    private lateinit var robot: FolderScreenRobot

    @Before
    fun setUp() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir = File(composeTestRule.activity.cacheDir, "test_selection_${System.currentTimeMillis()}")
            .apply { mkdirs() }
        robot = FolderScreenRobot(composeTestRule, testDir)
        // NAME_ASC with folders first: Documents, document.pdf, notes.txt, photo.jpg, video.mp4
        FileFixtures.createFolder(testDir, "Documents")
        FileFixtures.createTextFile(testDir, "document.pdf", "d")
        FileFixtures.createTextFile(testDir, "notes.txt", "n")
        FileFixtures.createTextFile(testDir, "photo.jpg", "p")
        FileFixtures.createTextFile(testDir, "video.mp4", "v")
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir.deleteRecursively()
    }

    private fun string(id: Int) = robot.string(id)
    private fun selectionTitle(count: Int) = robot.plural(R.plurals.selection_count, count)

    private fun renderAndWait() {
        robot.render()
        robot.waitForText("video.mp4")
    }

    // ==================== Entering selection mode ====================

    @Test
    fun longPress_entersSelectionMode() {
        renderAndWait()
        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertDoesNotExist()

        robot.longClick("document.pdf")

        robot.waitForText(selectionTitle(1))
        composeTestRule.onNodeWithText(selectionTitle(1)).assertIsDisplayed()
    }

    @Test
    fun longPress_showsActionBar() {
        renderAndWait()
        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertDoesNotExist()

        robot.longClick("document.pdf")

        robot.waitForText(string(R.string.action_move_to))
        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_copy_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
    }

    // ==================== Toggling ====================

    @Test
    fun selectionMode_tapAddsToSelection() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        robot.click("photo.jpg")

        robot.waitForText(selectionTitle(2))
        composeTestRule.onNodeWithText(selectionTitle(2)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_tapSelectedItem_deselects() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))

        robot.click("document.pdf")

        robot.waitForText(selectionTitle(1))
        composeTestRule.onNodeWithText(selectionTitle(1)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_multipleSelection_countsEveryItem() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.click("notes.txt")

        robot.waitForText(selectionTitle(3))
        composeTestRule.onNodeWithText(selectionTitle(3)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_titleShowsCount_singular() {
        renderAndWait()
        robot.longClick("document.pdf")

        robot.waitForText(selectionTitle(1))
        composeTestRule.onNodeWithText(selectionTitle(1)).assertIsDisplayed()
    }

    /**
     * A folder tapped in selection mode must be selected, not navigated into — the tap handler
     * branches on selection mode before it branches on `isDirectory`.
     */
    @Test
    fun selectionMode_tapFolder_selectsInsteadOfNavigating() {
        var navigatedPath: String? = null
        robot.render(onNavigateToFolder = { navigatedPath = it })
        robot.waitForText("video.mp4")

        robot.longClick("document.pdf")
        robot.click("Documents")

        robot.waitForText(selectionTitle(2))
        org.junit.Assert.assertNull("Tapping a folder in selection mode must not navigate", navigatedPath)
    }

    // ==================== Clearing ====================

    @Test
    fun selectionMode_closeButton_clearsSelectionAndHidesActionBar() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(string(R.string.action_move_to))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_clear_selection))
            .performClick()
        composeTestRule.waitForIdle()

        robot.waitForTextToDisappear(string(R.string.action_move_to))
        composeTestRule.onNodeWithText(selectionTitle(1)).assertDoesNotExist()
    }

    @Test
    fun selectionMode_lastItemDeselected_exitsMode() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(string(R.string.action_move_to))

        robot.click("document.pdf")

        robot.waitForTextToDisappear(string(R.string.action_move_to))
        composeTestRule.onNodeWithText(selectionTitle(1)).assertDoesNotExist()
    }

    @Test
    fun selectionMode_systemBack_exitsSelectionWithoutNavigating() {
        var backNavigated = false
        robot.render(onNavigateBack = { backNavigated = true })
        robot.waitForText("video.mp4")
        robot.longClick("document.pdf")
        robot.waitForText(string(R.string.action_move_to))

        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        robot.waitForTextToDisappear(string(R.string.action_move_to))
        org.junit.Assert.assertFalse(
            "Back in selection mode should only exit selection, not leave the folder",
            backNavigated
        )
    }

    // ==================== Select all ====================

    @Test
    fun selectionMode_selectAll_selectsEveryItem() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.action_select_all))
            .performClick()
        composeTestRule.waitForIdle()

        robot.waitForText(selectionTitle(5))
        composeTestRule.onNodeWithText(selectionTitle(5)).assertIsDisplayed()
    }

    /**
     * The row menu is the only way into the file actions sheet, whose Move and Copy entries acted
     * on the selection plus the tapped row. The action bar is what acts on a selection, so while
     * one exists no overflow is offered anywhere — the toolbar's included, since it is replaced.
     */
    @Test
    fun selectionMode_hidesEveryOverflowMenu() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        composeTestRule
            .onAllNodesWithContentDescription(string(R.string.content_description_more_options))
            .assertCountEquals(0)
    }

    @Test
    fun selectionMode_selectAllIcon_showsWhenNotAllSelected() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.action_select_all))
            .assertIsDisplayed()
    }

    @Test
    fun selectionMode_selectAllIcon_hiddenWhenAllSelected() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))
        composeTestRule
            .onNodeWithContentDescription(string(R.string.action_select_all))
            .performClick()
        robot.waitForText(selectionTitle(5))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.action_select_all))
            .assertDoesNotExist()
    }
}
