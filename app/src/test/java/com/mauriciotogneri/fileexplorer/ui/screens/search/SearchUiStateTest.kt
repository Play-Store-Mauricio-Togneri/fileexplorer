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
    fun showNoResults_falseWhenQueryEmpty() {
        val state = SearchUiState(
            query = "",
            isSearching = false,
            searchComplete = true,
            results = emptyList()
        )

        assertFalse("An empty query means the user has not searched yet", state.showNoResults)
    }

    /**
     * Also covers the last partial result being cleared when a search restarts: the state a
     * restart lands in is this one, and it must not flip the empty state on either.
     */
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

    /**
     * The 300 ms the debounce holds a keystroke for, which is the one window where the query is
     * non-empty and neither flag is set: [SearchViewModel.onQueryChange] clears `searchComplete`
     * on the keystroke, and `isSearching` only goes up when the search itself starts.
     *
     * Every other test here sets `isSearching` and `searchComplete` together, which over-determines
     * the `false` and lets `searchComplete` be swapped for `!isSearching` unnoticed. Under that
     * swap the empty state is on for the whole debounce window, so "No results found" flashes on
     * the first keystroke of every search the user types.
     */
    @Test
    fun showNoResults_falseWithinTheDebounceWindow() {
        val state = SearchUiState(
            query = "t",
            isSearching = false,
            searchComplete = false,
            results = emptyList()
        )

        assertFalse("The search has not started yet, let alone finished", state.showNoResults)
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
}
