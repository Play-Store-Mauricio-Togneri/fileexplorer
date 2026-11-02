package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
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
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Leaving the folder screen clears its ViewModel, and with it every delete, move or copy still
 * running there, part-way through. Once an operation's progress dialog is up it takes every press,
 * but the dialog waits out a delay first, and a way out taken in that window used to leave the
 * screen with nothing to say the work had stopped. Each test here presses one way out while a delete
 * is inside its delay, and asserts the press shows the dialog instead of leaving.
 *
 * The delete never finishes, so it is still running whenever the test presses. The press runs on
 * the main thread in the same block that checks the dialog is still held back: the delay's timer
 * runs on that thread too, so it cannot show the dialog between the check and the press. Without
 * that the press could land on a dialog the timer already showed, and pass with the defect present.
 *
 * Rendered without the navigation host, so leaving is observed where the host would take it: a back
 * callback registered beneath the screen's own handlers for system back — as the host's is — and
 * the screen's two navigation callbacks for the rest. System back can leave through either: the
 * host's handler when the screen lets the press through, or the screen's own back callback, which
 * its handler calls when there is no dialog to show.
 */
@RunWith(AndroidJUnit4::class)
class FolderLeaveDuringProgressDelayTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File
    private lateinit var ledgers: File
    private lateinit var work: File
    private lateinit var doomed: File
    private lateinit var viewModel: FolderViewModel

    @Volatile
    private var leftBySystemBack = false

    @Volatile
    private var navigatedBack = false

    @Volatile
    private var navigatedTo: String? = null

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_leave_delay_${System.currentTimeMillis()}").apply { mkdirs() }
        ledgers = FileFixtures.createFolder(testDir, "Ledgers")
        work = FileFixtures.createFolder(ledgers, "Work")
        doomed = FileFixtures.createTextFile(work, "doomed.txt", "d")
        viewModel = buildViewModel()

        // Added before the screen composes, so the screen's back handlers outrank it the way they
        // outrank the navigation host's.
        composeTestRule.runOnUiThread {
            activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    leftBySystemBack = true
                }
            })
        }
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = work.absolutePath,
                    onNavigateToFolder = { navigatedTo = it },
                    onNavigateBack = { navigatedBack = true },
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(doomed.name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        // The ViewModel is built here rather than by the screen, so nothing else would stop the walk.
        composeTestRule.runOnUiThread { viewModel.cancelDelete() }
        testDir.deleteRecursively()
    }

    @Test
    fun systemBack_whileTheDeleteDialogIsHeldBack_showsItInsteadOfLeaving() {
        pressDuringDelay { activity.onBackPressedDispatcher.onBackPressed() }

        assertFalse("System back must not leave a folder whose delete is still running", leftBySystemBack)
        assertFalse("System back must not leave through the screen's own back callback either", navigatedBack)
        assertDeleteDialogShown()
    }

    @Test
    fun toolbarBackArrow_whileTheDeleteDialogIsHeldBack_showsItInsteadOfLeaving() {
        val tapBackArrow = clickActionOf(composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)))

        pressDuringDelay(tapBackArrow)

        assertFalse("The back arrow must not leave a folder whose delete is still running", navigatedBack)
        assertDeleteDialogShown()
    }

    @Test
    fun ancestorBreadcrumb_whileTheDeleteDialogIsHeldBack_showsItInsteadOfLeaving() {
        val tapLedgers = clickActionOf(composeTestRule.onNodeWithText(ledgers.name))

        pressDuringDelay(tapLedgers)

        assertTrue(
            "A breadcrumb must not leave a folder whose delete is still running, but it opened $navigatedTo",
            navigatedTo == null
        )
        assertDeleteDialogShown()
    }

    // ==================== Helpers ====================

    /**
     * Confirms a delete of [doomed], lets the screen recompose, then runs [press] while the delete's
     * dialog is still held back, failing if the delay ran out first.
     */
    private fun pressDuringDelay(press: () -> Unit) {
        composeTestRule.runOnUiThread {
            viewModel.showDeleteConfirmDialog(listOf(FileItem.from(doomed)))
            viewModel.onDeleteConfirmed()
        }
        composeTestRule.waitForIdle()

        val pressedWhileHeldBack = composeTestRule.runOnUiThread {
            val heldBack = viewModel.state.value.deleteProgress == null
            if (heldBack) press()
            heldBack
        }
        composeTestRule.waitForIdle()

        assertTrue("The delete's dialog appeared before the test could press", pressedWhileHeldBack)
    }

    /**
     * The click a tap on [node] runs, fetched ahead so it can be invoked on the main thread. The
     * semantics click is the same lambda the node's clickable runs on a touch.
     */
    private fun clickActionOf(node: SemanticsNodeInteraction): () -> Unit {
        val action = checkNotNull(node.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        return { action() }
    }

    private fun assertDeleteDialogShown() {
        composeTestRule.onNodeWithText(string(R.string.delete_deleting)).assertIsDisplayed()
    }

    private fun buildViewModel(): FolderViewModel {
        val app = activity.application
        return FolderViewModel(
            application = app,
            initialPath = work.absolutePath,
            initialTitle = null,
            fileRepository = StalledDeleteRepository(),
            preferencesRepository = PreferencesRepository(DataStorePreferencesSource(app.preferencesDataStore)),
            storageRepository = StorageRepository(FakeStorageSource(testDir)),
            favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(app.favoriteFilesDataStore)),
            recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(app.recentFilesDataStore))
        )
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    /** Lists the real folder, and deletes nothing: its walk reports nothing and never finishes. */
    private class StalledDeleteRepository : FileRepository() {
        override fun deleteWithProgress(files: List<FileItem>): Flow<DeleteProgress> = flow {
            awaitCancellation()
        }
    }
}
