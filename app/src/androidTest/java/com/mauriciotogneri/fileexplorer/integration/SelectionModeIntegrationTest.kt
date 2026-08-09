package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import com.mauriciotogneri.fileexplorer.testutil.buttonWithText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Which bulk actions the real `ActionBar` offers for a given selection, reached through the real
 * [com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen].
 *
 * The visibility rules are the point here: Share is files-only, Rename is single-selection-only, and
 * both must disappear as soon as the selection stops qualifying. The previous version asserted these
 * against hardcoded English literals (`onNodeWithText("Share").assertDoesNotExist()`), which pass on
 * any non-English device regardless of what the bar actually shows.
 */
@RunWith(AndroidJUnit4::class)
class SelectionModeIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var testDir: File
    private lateinit var robot: FolderScreenRobot

    @Before
    fun setUp() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir = File(composeTestRule.activity.cacheDir, "test_selection_int_${System.currentTimeMillis()}")
            .apply { mkdirs() }
        robot = FolderScreenRobot(composeTestRule, testDir)
        FileFixtures.createFolder(testDir, "Documents")
        FileFixtures.createTextFile(testDir, "document.pdf", "d")
        FileFixtures.createTextFile(testDir, "notes.txt", "n")
        FileFixtures.createTextFile(testDir, "photo.jpg", "p")
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
        robot.waitForText("photo.jpg")
    }

    // ==================== Always-available actions ====================

    @Test
    fun selectionMode_multipleFiles_offersMoveCopyDelete() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.click("notes.txt")
        robot.waitForText(selectionTitle(3))

        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_copy_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_compress)).assertIsDisplayed()
    }

    /**
     * `action_delete`, `delete_confirm_title` and `dialog_delete` all read "Delete", so the bar
     * button, the dialog title and the dialog's confirm button are indistinguishable by text. The
     * confirm button is the one that only exists once the dialog is up, and its Material role is
     * what [buttonWithText] uses to tell it apart — the bar button is a bare clickable.
     */
    @Test
    fun selectionMode_delete_opensConfirmationForTheWholeSelection() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))

        robot.click(string(R.string.action_delete))

        composeTestRule.onNode(buttonWithText(string(R.string.dialog_delete))).assertExists()
        composeTestRule.onNodeWithText(robot.plural(R.plurals.item_amount, 2)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_moveTo_opensDestinationPicker() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        robot.click(string(R.string.action_move_to))

        awaitPicker(selectedCount = 1, title = string(R.string.picker_title_move))
        composeTestRule.onNodeWithText(string(R.string.picker_title_move)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_copyTo_opensDestinationPicker() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        robot.click(string(R.string.action_copy_to))

        awaitPicker(selectedCount = 1, title = string(R.string.picker_title_copy))
        composeTestRule.onNodeWithText(string(R.string.picker_title_copy)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_compress_opensCompressDialog() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))

        robot.click(string(R.string.action_compress))

        composeTestRule.onNodeWithText(".zip").assertIsDisplayed()
    }

    // ==================== Share: files only ====================

    @Test
    fun selectionMode_allFiles_offersShare() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_folderSelected_hidesShare() {
        renderAndWait()
        robot.longClick("Documents")
        robot.waitForText(selectionTitle(1))

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertDoesNotExist()
    }

    @Test
    fun selectionMode_mixedSelection_hidesShare() {
        renderAndWait()
        robot.longClick("document.pdf")
        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()

        robot.click("Documents")
        robot.waitForText(selectionTitle(2))

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertDoesNotExist()
    }

    // ==================== Rename: single selection only ====================

    @Test
    fun selectionMode_singleSelection_offersRename() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        composeTestRule.onNodeWithText(string(R.string.action_rename)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_secondItemSelected_hidesRename() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))
        composeTestRule.onNodeWithText(string(R.string.action_rename)).assertIsDisplayed()

        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))

        composeTestRule.onNodeWithText(string(R.string.action_rename)).assertDoesNotExist()
    }

    @Test
    fun selectionMode_backToSingleSelection_offersRenameAgain() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(2))
        composeTestRule.onNodeWithText(string(R.string.action_rename)).assertDoesNotExist()

        robot.click("photo.jpg")
        robot.waitForText(selectionTitle(1))

        composeTestRule.onNodeWithText(string(R.string.action_rename)).assertIsDisplayed()
    }

    /**
     * Same collision as the delete case: the bar button, the dialog title and its confirm button all
     * read "Rename". The name is asserted on the editable field rather than by text alone, since the
     * row behind the dialog carries the same name.
     */
    @Test
    fun selectionMode_rename_opensDialogPrefilledWithTheSelection() {
        renderAndWait()
        robot.longClick("document.pdf")
        robot.waitForText(selectionTitle(1))

        robot.click(string(R.string.action_rename))

        composeTestRule.onNode(buttonWithText(string(R.string.dialog_rename))).assertExists()
        composeTestRule.onNode(hasSetTextAction() and hasText("document.pdf")).assertIsDisplayed()
    }

    /**
     * Waits out the action bar first — opening the picker clears the [selectedCount] selection, and
     * until that lands the bar's own "Move to" / "Copy to" labels are still on screen and would
     * collide with [title], which is the same string — then waits for the picker to slide in.
     *
     * [selectedCount] must be what the caller actually selected: the wait is for that count's label
     * to go away, and a wrong one returns immediately on text that was never there.
     *
     * The title rather than the confirm button, because the button lives in a bottom bar the picker
     * hides while its storage selector is up — and the selector appears whenever the device has more
     * than one volume, which the SD-card-equipped emulator does. This screen builds its own
     * `StorageRepository(AndroidStorageSource(context))`, so unlike `FileOperationIntegrationTest`
     * and `PickerCreateFolderTest` it cannot fake the volume list. Those two own the confirm button
     * against the real `DestinationPicker`; what is asserted here is only that the picker is what the
     * action opens.
     */
    private fun awaitPicker(selectedCount: Int, title: String) {
        robot.waitForTextToDisappear(selectionTitle(selectedCount))
        robot.waitForText(title)
    }
}
