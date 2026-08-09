package com.mauriciotogneri.fileexplorer.ui.screens.search

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchUiState.showNoResults] is pure state logic with no Android dependency, so it belongs here
 * rather than in the instrumentation suite where it used to sit (inside `SearchBehaviorTest`) and
 * paid for an emulator it never used.
 *
 * The rule has three guards and each one matters: an empty query means the user has not searched
 * yet, an incomplete search means results may still stream in, and a non-empty result list is
 * self-evidently not "no results". Dropping any one of them shows the empty state at a moment the
 * user would read as "your files are gone".
 */
class SearchUiStateTest {

    private val file = FileItem(
        path = "/storage/emulated/0/test.txt",
        name = "test.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = 0L,
        createdTime = 0L,
        mimeType = "text/plain",
        childCount = null
    )

    @Test
    fun initialState_hasCorrectDefaults() {
        val state = SearchUiState()

        assertTrue("Query should be empty", state.query.isEmpty())
        assertTrue("Results should be empty", state.results.isEmpty())
        assertFalse("Should not be searching", state.isSearching)
        assertFalse("Should not be complete", state.searchComplete)
        assertFalse("Should not show no results", state.showNoResults)
    }

    @Test
    fun showNoResults_falseWhenQueryEmpty() {
        val state = SearchUiState(
            query = "",
            isSearching = false,
            searchComplete = true,
            results = emptyList()
        )

        assertFalse("An empty query means the user has not searched yet", state.showNoResults)
    }

    @Test
    fun showNoResults_falseWhileStillSearching() {
        val state = SearchUiState(
            query = "test",
            isSearching = true,
            searchComplete = false,
            results = emptyList()
        )

        assertFalse("Results may still stream in", state.showNoResults)
    }

    @Test
    fun showNoResults_falseWhenResultsExist() {
        val state = SearchUiState(
            query = "test",
            isSearching = false,
            searchComplete = true,
            results = listOf(file)
        )

        assertFalse("There are results", state.showNoResults)
    }

    @Test
    fun showNoResults_trueWhenCompleteWithNoResults() {
        val state = SearchUiState(
            query = "nonexistent",
            isSearching = false,
            searchComplete = true,
            results = emptyList()
        )

        assertTrue("A finished search with nothing found is the empty state", state.showNoResults)
    }

    /**
     * The last partial result being cleared mid-search must not flip the empty state on, or the
     * message flashes while the search is still running.
     */
    @Test
    fun showNoResults_falseWhenSearchingRestartsAfterResults() {
        val state = SearchUiState(
            query = "test",
            isSearching = true,
            searchComplete = false,
            results = emptyList()
        )

        assertFalse(state.showNoResults)
    }
}
