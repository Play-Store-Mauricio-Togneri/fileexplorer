package com.mauriciotogneri.fileexplorer.integration

import android.os.Environment
import android.system.Os
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A move driven through the real [FolderScreen], with a real [FolderViewModel] over a real
 * [FileRepository]: the rename step, the copy it falls back to, and the screen they leave behind.
 * `MoveByRenameTest` covers the rename step on its own; this covers what the user sees of the two
 * together.
 *
 * The items are selected and the move is started through the screen's own selection bar, and the
 * picker it opens is checked for. The destination is then confirmed through
 * [FolderViewModel.executeOperation], the call the picker's confirm button makes (`FolderScreen`
 * passes it as the picker's `onConfirm`), rather than by browsing to it: the picker builds its own
 * storage list from the device's real volumes, so the temporary folders these tests use under the
 * app's cache cannot be browsed to in it. The picker's own browsing and confirm button are covered
 * by `FileOperationIntegrationTest` and `PickerBottomBarTest`.
 *
 * Built like `UncompressFlowTest`: the ViewModel's storage roots are faked to the temporary folders,
 * so the move's allowed-roots check accepts them.
 */
@RunWith(AndroidJUnit4::class)
class MoveFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private val stamp = System.currentTimeMillis()
    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var robot: FolderScreenRobot
    private lateinit var viewModel: FolderViewModel
    private var sdCardTestRoot: File? = null

    @Before
    fun setUp() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir = File(activity.cacheDir, "test_move_flow_$stamp").apply { mkdirs() }
        sourceDir = File(testDir, "source").apply { mkdirs() }
        targetDir = File(testDir, "target").apply { mkdirs() }
        robot = FolderScreenRobot(composeTestRule, sourceDir)
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir.deleteRecursively()
        sdCardTestRoot?.deleteRecursively()
    }

    // ==================== Same drive ====================

    /**
     * One item whose name is free moves by rename; one whose name is taken is left to the copy,
     * which numbers it rather than replacing the file already there. Both leave the folder on
     * screen, and the dialog the two-item move showed is gone once it settles.
     */
    @Test
    fun mixedSelection_renamesTheFreeName_andNumbersTheTakenOne() {
        val fresh = FileFixtures.createTextFile(sourceDir, "fresh.txt", "fresh content")
        FileFixtures.createTextFile(sourceDir, "clash.txt", "moving content")
        FileFixtures.createTextFile(targetDir, "clash.txt", "already there")
        // A rename keeps the inode on the app's own storage; a copy makes a new file.
        val freshInode = Os.stat(fresh.path).st_ino
        renderFolder(rootsOf(testDir))

        robot.waitForText("fresh.txt")
        robot.longClick("clash.txt")
        robot.click("fresh.txt")
        robot.waitForText(selectionTitle(2))
        startMoveTo(targetDir)

        waitUntil { File(targetDir, "fresh.txt").exists() && File(targetDir, "clash (1).txt").exists() }
        waitUntilSettled("fresh.txt", "clash.txt")

        val movedFresh = File(targetDir, "fresh.txt")
        assertEquals("fresh content", movedFresh.readText())
        assertEquals("The free name must move by rename, not by copy", freshInode, Os.stat(movedFresh.path).st_ino)
        assertFalse(File(sourceDir, "fresh.txt").exists())
        assertEquals("moving content", File(targetDir, "clash (1).txt").readText())
        assertEquals("The file already there must be untouched", "already there", File(targetDir, "clash.txt").readText())
        assertFalse(File(sourceDir, "clash.txt").exists())
    }

    /** A folder whose name is taken is merged into the one there by the copy, never replacing it. */
    @Test
    fun folderWithATakenName_isMergedIntoTheOneThere() {
        val album = FileFixtures.createFolder(sourceDir, "summer_trip")
        FileFixtures.createTextFile(album, "new.jpg", "new")
        val existing = FileFixtures.createFolder(targetDir, "summer_trip")
        FileFixtures.createTextFile(existing, "old.jpg", "old")
        renderFolder(rootsOf(testDir))

        robot.waitForText("summer_trip")
        robot.longClick("summer_trip")
        robot.waitForText(selectionTitle(1))
        startMoveTo(targetDir)

        waitUntil { File(existing, "new.jpg").exists() && !album.exists() }
        waitUntilSettled("summer_trip")

        assertEquals("new", File(existing, "new.jpg").readText())
        assertEquals("The folder's own files must be untouched", "old", File(existing, "old.jpg").readText())
        assertFalse("A merge must not create a numbered folder", File(targetDir, "summer_trip (1)").exists())
    }

    // ==================== Across drives ====================

    /**
     * A rename cannot cross volumes, so the move to the SD card is the copy it always was: the file
     * arrives and its original is gone. The destination is the app's own folder on the card, which
     * it can write without All Files Access.
     */
    @Test
    fun moveToTheSdCard_fallsBackToTheCopy() {
        val sdRoot = sdCardTestRootOrSkip()
        val sdTarget = File(sdRoot, "target").apply { mkdirs() }
        FileFixtures.createTextFile(sourceDir, "travelling.txt", "crossing volumes")
        renderFolder(rootsOf(testDir, sdRoot))

        robot.waitForText("travelling.txt")
        robot.longClick("travelling.txt")
        robot.waitForText(selectionTitle(1))
        startMoveTo(sdTarget)

        waitUntil { File(sdTarget, "travelling.txt").exists() && !File(sourceDir, "travelling.txt").exists() }
        waitUntilSettled("travelling.txt")

        assertEquals("crossing volumes", File(sdTarget, "travelling.txt").readText())
    }

    /**
     * Across volumes the copy settles a name clash as it does on one drive: a taken file name is
     * numbered and a taken folder is merged, and nothing already on the card is replaced.
     */
    @Test
    fun moveToTheSdCard_numbersATakenFileAndMergesATakenFolder() {
        val sdRoot = sdCardTestRootOrSkip()
        val sdTarget = File(sdRoot, "target").apply { mkdirs() }
        FileFixtures.createTextFile(sourceDir, "clash.txt", "moving content")
        FileFixtures.createTextFile(sdTarget, "clash.txt", "already there")
        val trip = FileFixtures.createFolder(sourceDir, "winter_trip")
        FileFixtures.createTextFile(trip, "new.jpg", "new")
        val existing = FileFixtures.createFolder(sdTarget, "winter_trip")
        FileFixtures.createTextFile(existing, "old.jpg", "old")
        renderFolder(rootsOf(testDir, sdRoot))

        robot.waitForText("clash.txt")
        robot.longClick("clash.txt")
        robot.click("winter_trip")
        robot.waitForText(selectionTitle(2))
        startMoveTo(sdTarget)

        waitUntil {
            File(sdTarget, "clash (1).txt").exists() && File(existing, "new.jpg").exists() && !trip.exists()
        }
        waitUntilSettled("clash.txt", "winter_trip")

        assertEquals("moving content", File(sdTarget, "clash (1).txt").readText())
        assertEquals("The file already there must be untouched", "already there", File(sdTarget, "clash.txt").readText())
        assertEquals("new", File(existing, "new.jpg").readText())
        assertEquals("The folder's own files must be untouched", "old", File(existing, "old.jpg").readText())
        assertFalse("A merge must not create a numbered folder", File(sdTarget, "winter_trip (1)").exists())
        assertFalse(File(sourceDir, "clash.txt").exists())
    }

    // ==================== Helpers ====================

    /**
     * A fresh folder in the app's own folder on a mounted SD card, which it can write without All
     * Files Access, or a skip when the device has none. Read from the platform's volume state, never
     * from the app's storage code.
     */
    private fun sdCardTestRootOrSkip(): File {
        val sdFilesDir = activity.getExternalFilesDirs(null).firstOrNull { dir ->
            dir != null &&
                Environment.isExternalStorageRemovable(dir) &&
                Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
        }
        assumeTrue("Device has no mounted removable volume", sdFilesDir != null)
        return File(sdFilesDir!!, "test_move_flow_$stamp").apply { mkdirs() }
            .also { sdCardTestRoot = it }
    }

    private fun renderFolder(storageSource: StorageSource) {
        composeTestRule.setContent {
            val folderViewModel = remember { buildViewModel(storageSource).also { viewModel = it } }
            FileExplorerTheme {
                FolderScreen(
                    path = sourceDir.absolutePath,
                    onNavigateToFolder = {},
                    onNavigateBack = {},
                    viewModel = folderViewModel
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun buildViewModel(storageSource: StorageSource): FolderViewModel {
        val app = activity.application
        return FolderViewModel(
            application = app,
            initialPath = sourceDir.absolutePath,
            initialTitle = null,
            fileRepository = FileRepository(),
            preferencesRepository = PreferencesRepository(DataStorePreferencesSource(app.preferencesDataStore)),
            storageRepository = StorageRepository(storageSource),
            favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(app.favoriteFilesDataStore)),
            recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(app.recentFilesDataStore))
        )
    }

    /** Storage roots for the move's allowed-roots check: the temporary folders, one per volume. */
    private fun rootsOf(vararg roots: File): StorageSource = object : StorageSource {
        override suspend fun getStorages(): List<StorageDevice> = roots.mapIndexed { index, root ->
            StorageDevice(
                path = root.absolutePath,
                displayName = "Test Storage $index",
                totalBytes = 1_000_000_000L,
                availableBytes = 500_000_000L,
                type = if (index == 0) StorageType.INTERNAL else StorageType.SD_CARD
            )
        }
    }

    /**
     * Starts the move from the selection bar, waits for the picker it opens, and confirms [target]
     * the way the picker's confirm button does.
     */
    private fun startMoveTo(target: File) {
        robot.click(string(R.string.action_move_to))
        robot.waitForText(string(R.string.picker_title_move))
        composeTestRule.runOnUiThread { viewModel.executeOperation(target.absolutePath) }
        composeTestRule.waitForIdle()
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) { condition() }
    }

    /**
     * Waits for the moved rows to leave the listing and for the picker and the progress dialog to
     * close, then checks the dialog is really gone rather than merely between two states.
     */
    private fun waitUntilSettled(vararg movedNames: String) {
        movedNames.forEach { robot.waitForTextToDisappear(it, OPERATION_TIMEOUT_MS) }
        robot.waitForTextToDisappear(string(R.string.picker_title_move), OPERATION_TIMEOUT_MS)
        robot.waitForTextToDisappear(string(R.string.progress_moving), OPERATION_TIMEOUT_MS)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.progress_moving)).assertDoesNotExist()
        assertEquals(
            "No moved item may still be listed",
            0,
            movedNames.sumOf { composeTestRule.onAllNodesWithText(it).fetchSemanticsNodes().size }
        )
    }

    private fun selectionTitle(count: Int) = robot.plural(R.plurals.selection_count, count)

    private fun string(@StringRes id: Int): String = activity.getString(id)

    private companion object {
        const val OPERATION_TIMEOUT_MS = 20_000L
    }
}
