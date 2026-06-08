package com.mauriciotogneri.fileexplorer.integration

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.ui.screens.search.SearchScreen
import com.mauriciotogneri.fileexplorer.ui.screens.search.SearchViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 8 (Point 8) — search scoping. Verifies that real streaming search results are scoped to the
 * storage root(s) the [SearchViewModel] is given, matched by file name, recursing into subfolders.
 *
 * Real-wiring: a real [SearchViewModel] built over `StorageRepository(FakeStorageSource(testDir))`
 * (so the only search root is the temp dir) + the real [SearchScreen]. A matching file placed
 * OUTSIDE the fake root must never surface.
 *
 * Note: `SearchActivity` does not consume a `root` argument (its route is a placeholder), so scoping
 * is asserted at the ViewModel level via injected storages, not via launch args. The Home -> Search
 * launch itself is covered by HomeSearchLaunchTest.
 */
@RunWith(AndroidJUnit4::class)
class SearchScopingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application = context.applicationContext as Application

    private lateinit var testDir: File
    private lateinit var outsideDir: File

    @Before
    fun setUp() {
        val stamp = System.currentTimeMillis()
        testDir = File(context.cacheDir, "test_search_$stamp").apply { mkdirs() }
        outsideDir = File(context.cacheDir, "test_search_outside_$stamp").apply { mkdirs() }

        File(testDir, "report_alpha.txt").writeText("a")
        File(testDir, "report_beta.txt").writeText("b")
        File(testDir, "notes.txt").writeText("c")
        File(testDir, "sub").mkdirs()
        File(testDir, "sub/report_gamma.txt").writeText("d") // nested match (recursion)
        File(outsideDir, "report_outside.txt").writeText("e") // outside the fake root
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
        outsideDir.deleteRecursively()
    }

    @Test
    fun search_returnsMatchesWithinRoot_includingNested() {
        renderSearch()
        typeQuery("report")

        // Results stream in unspecified File.listFiles() order, so wait for each match
        // (including the nested one) before asserting rather than gating on just the first.
        waitForText("report_alpha.txt")
        waitForText("report_beta.txt")
        waitForText("report_gamma.txt")
        composeTestRule.onNodeWithText("report_alpha.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("report_beta.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("report_gamma.txt").assertIsDisplayed()
    }

    @Test
    fun search_excludesNonMatchingFiles() {
        renderSearch()
        typeQuery("report")

        waitForText("report_alpha.txt")
        composeTestRule.onNodeWithText("notes.txt").assertDoesNotExist()
    }

    @Test
    fun search_excludesFilesOutsideRoot() {
        renderSearch()
        typeQuery("report")

        waitForText("report_alpha.txt")
        // The matching file under outsideDir is not under the fake storage root, so it is never found.
        composeTestRule.onNodeWithText("report_outside.txt").assertDoesNotExist()
    }

    @Test
    fun search_clearQuery_resetsResults() {
        renderSearch()
        typeQuery("report")
        waitForText("report_alpha.txt")

        composeTestRule.onNodeWithContentDescription(string(R.string.search_clear)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("report_alpha.txt").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("report_alpha.txt").assertDoesNotExist()
    }

    @Test
    fun search_backButton_invokesOnBackClick() {
        var backClicked = false
        renderSearch(onBackClick = { backClicked = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        assertTrue("Back button should invoke onBackClick", backClicked)
    }

    // ==================== Helpers ====================

    private fun renderSearch(onBackClick: () -> Unit = {}) {
        val viewModel = SearchViewModel(
            application = application,
            fileRepository = FileRepository(),
            storageRepository = StorageRepository(FakeStorageSource(testDir)),
            preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchScreen(onBackClick = onBackClick, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun typeQuery(query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)
}
