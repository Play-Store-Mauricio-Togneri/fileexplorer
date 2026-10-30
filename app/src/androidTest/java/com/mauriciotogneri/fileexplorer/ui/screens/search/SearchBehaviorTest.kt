package com.mauriciotogneri.fileexplorer.ui.screens.search

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The empty / results / no-results states of the real [SearchScreen], driven by a real
 * [SearchViewModel] over a temp directory.
 *
 * The previous version rendered a private `TestSearchResultsContent` that re-declared
 * `showNoResults` as `query.isNotEmpty() && searchComplete && results.isEmpty()` — the exact
 * expression under test — so the copy could never disagree with the production rule.
 *
 * The pure `SearchUiState` cases moved to `app/src/test/.../SearchUiStateTest`, where they run
 * without an emulator.
 */
@RunWith(AndroidJUnit4::class)
class SearchBehaviorTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application = context.applicationContext as Application

    private lateinit var testDir: File

    /**
     * Holds a search open where a test parks it — in the storage lookup or after the first match —
     * so the in-flight UI stays on screen until the assertions are done.
     */
    private val releaseGate = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "test_search_behavior_${System.currentTimeMillis()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        // Never leave a search parked on the gate, even if an assertion failed.
        releaseGate.complete(Unit)
        testDir.deleteRecursively()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun renderSearch(
        storageSource: StorageSource = FakeStorageSource(testDir),
        fileRepository: FileRepository = FileRepository()
    ) {
        val viewModel = SearchViewModel(
            application = application,
            fileRepository = fileRepository,
            storageRepository = StorageRepository(storageSource),
            preferencesRepository = PreferencesRepository(
                DataStorePreferencesSource(application.preferencesDataStore)
            ),
            favoritesRepository = FavoritesRepository(
                DataStoreFavoriteFilesSource(application.favoriteFilesDataStore)
            )
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchScreen(onBackClick = {}, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun typeQuery(query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The search's progress indicator, whichever of its two places it is drawn in. */
    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    /** Waits past the query's debounce for the search to start and draw its spinner. */
    private fun waitForSpinner() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(spinner).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoSpinner() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(spinner).fetchSemanticsNodes().isEmpty()
        }
    }

    /** The test directory as the only storage, looked up only once [releaseGate] opens. */
    private inner class GatedStorageSource : StorageSource {
        private val storages = FakeStorageSource(testDir)

        override suspend fun getStorages(): List<StorageDevice> {
            releaseGate.await()
            return storages.getStorages()
        }
    }

    /** A search that streams [first] at once, then holds open until [releaseGate] opens. */
    private inner class HeldSearchRepository(private val first: FileItem) : FileRepository() {
        override fun searchFilesStreaming(
            rootPath: String,
            query: String,
            allowedRoots: List<String>,
            filters: SearchFilters,
            maxResults: Int
        ): Flow<FileItem> = flow {
            emit(first)
            releaseGate.await()
        }
    }

    // ==================== Empty state ====================

    /** Before anything is typed there is nothing to report, so the empty message must stay hidden. */
    @Test
    fun search_emptyQuery_showsNoEmptyMessage() {
        FileFixtures.createTextFile(testDir, "anything.txt", "a")
        renderSearch()

        composeTestRule.onNodeWithText(string(R.string.search_no_results)).assertDoesNotExist()
    }

    @Test
    fun search_noMatches_showsNoResultsMessage() {
        FileFixtures.createTextFile(testDir, "anything.txt", "a")
        renderSearch()

        typeQuery("nonexistentfile")

        waitForText(string(R.string.search_no_results))
        composeTestRule.onNodeWithText(string(R.string.search_no_results)).assertIsDisplayed()
    }

    @Test
    fun search_withMatches_hidesNoResultsMessage() {
        FileFixtures.createTextFile(testDir, "report_alpha.txt", "a")
        renderSearch()

        typeQuery("report")

        waitForText("report_alpha.txt")
        composeTestRule.onNodeWithText(string(R.string.search_no_results)).assertDoesNotExist()
    }

    /**
     * Clearing back to an empty query must return to the neutral state rather than leaving the
     * "no results" message from the previous search on screen.
     */
    @Test
    fun search_afterClearing_returnsToNeutralState() {
        FileFixtures.createTextFile(testDir, "anything.txt", "a")
        renderSearch()
        typeQuery("nonexistentfile")
        waitForText(string(R.string.search_no_results))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.search_clear))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(string(R.string.search_no_results))
                .fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText(string(R.string.search_no_results)).assertDoesNotExist()
    }

    // ==================== Results ====================

    @Test
    fun search_displaysEveryMatch() {
        (1..5).forEach { FileFixtures.createTextFile(testDir, "file$it.txt", "x") }
        renderSearch()

        typeQuery("file")

        (1..5).forEach { waitForText("file$it.txt") }
        (1..5).forEach { composeTestRule.onNodeWithText("file$it.txt").assertIsDisplayed() }
    }

    @Test
    fun search_recursesIntoSubfolders() {
        val nested = FileFixtures.createFolder(testDir, "sub")
        FileFixtures.createTextFile(nested, "report_nested.txt", "n")
        renderSearch()

        typeQuery("report")

        waitForText("report_nested.txt")
        composeTestRule.onNodeWithText("report_nested.txt").assertIsDisplayed()
    }

    @Test
    fun search_matchIsCaseInsensitive() {
        FileFixtures.createTextFile(testDir, "Report_Alpha.txt", "a")
        renderSearch()

        typeQuery("report")

        waitForText("Report_Alpha.txt")
        composeTestRule.onNodeWithText("Report_Alpha.txt").assertIsDisplayed()
    }

    // ==================== In flight ====================

    /**
     * Before the first match arrives the screen shows a spinner, and not the "no results" message:
     * nothing has been searched yet. No test held a search open, so a screen that dropped the
     * spinner — or reported no results while still searching — passed.
     */
    @Test
    fun search_inFlightBeforeFirstMatch_showsSpinner() {
        FileFixtures.createTextFile(testDir, "report_alpha.txt", "a")
        renderSearch(storageSource = GatedStorageSource())

        typeQuery("report")
        waitForSpinner()

        composeTestRule.onNodeWithText(string(R.string.search_no_results)).assertDoesNotExist()

        releaseGate.complete(Unit)
        waitForText("report_alpha.txt")
        waitForNoSpinner()
        composeTestRule.onNode(spinner).assertDoesNotExist()
    }

    /**
     * Once rows are shown, a search still running keeps its spinner under them, so the user can
     * tell more may come. Without a search held mid-stream the in-list spinner never rendered in
     * any test.
     */
    @Test
    fun search_inFlightAfterFirstMatch_keepsSpinnerBelowRows() {
        val first = FileItem.from(FileFixtures.createTextFile(testDir, "report_alpha.txt", "a"))
        renderSearch(fileRepository = HeldSearchRepository(first))

        typeQuery("report")
        waitForText("report_alpha.txt")
        waitForSpinner()

        val row = composeTestRule.onNodeWithText("report_alpha.txt")
        row.assertIsDisplayed()
        val indicator = composeTestRule.onNode(spinner)
        indicator.assertIsDisplayed()
        val rowBottom = row.getUnclippedBoundsInRoot().bottom
        val indicatorTop = indicator.getUnclippedBoundsInRoot().top
        assertTrue(
            "The spinner must sit below the rows: its top $indicatorTop, the row's bottom $rowBottom",
            indicatorTop >= rowBottom
        )
    }

    // Non-matching files being excluded is owned by SearchScopingTest, which also covers the
    // storage-root boundary; duplicating it here would pay for the same scenario twice.

    /**
     * The kind filter starts on `SearchItemKind.FILES`, so a folder is reachable only after
     * switching it. That default is nothing this test asserts — it is the reason for the extra step,
     * and it has no coverage of its own anywhere in the suite.
     */
    @Test
    fun search_withFoldersKind_findsFoldersByName() {
        FileFixtures.createFolder(testDir, "report_folder")
        renderSearch()

        selectFoldersKind()
        typeQuery("report")

        waitForText("report_folder")
        composeTestRule.onNodeWithText("report_folder").assertIsDisplayed()
    }

    /** Opens the kind chip (labelled with its current value, Files) and picks Folders. */
    private fun selectFoldersKind() {
        composeTestRule.onNodeWithText(string(R.string.search_filter_kind_files)).performClick()
        composeTestRule.onNodeWithText(string(R.string.search_filter_kind_folders)).performClick()
        composeTestRule.waitForIdle()
    }
}
