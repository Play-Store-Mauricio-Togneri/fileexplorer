package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import com.mauriciotogneri.fileexplorer.data.model.PdfSearchMatch
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelSearchTest {

    @get:Rule
    val rule = PdfViewerTestRule()

    private val a = PdfRectPt(1f, 1f, 2f, 2f)
    private val b = PdfRectPt(3f, 3f, 4f, 4f)
    private val c = PdfRectPt(5f, 5f, 6f, 6f)

    /** Matches on pages 1 and 3 of four, none on 0 or 2. */
    private fun document() = FakePdfDocumentSource(
        pageSizes = List(4) { PdfPageSize(100, 100) },
        matches = mapOf(1 to listOf(listOf(a), listOf(b)), 3 to listOf(listOf(c)))
    )

    private fun loaded(document: FakePdfDocumentSource = document(), mode: PdfViewerMode = PdfViewerMode.FULL) =
        rule.viewModel(FakePdfDocumentOpener(mode = mode, document = document)).also {
            rule.advance()
            it.openSearch()
        }

    @Test
    fun `every match in the document is found, in page order, and the first is current`() {
        val viewModel = loaded()
        viewModel.onSearchQueryChange("  zebra ")
        viewModel.submitSearch()
        rule.advance()

        val search = viewModel.state.value.search
        assertEquals(
            listOf(PdfSearchMatch(1, listOf(a)), PdfSearchMatch(1, listOf(b)), PdfSearchMatch(3, listOf(c))),
            search.matches
        )
        assertEquals(0, search.currentIndex)
        assertEquals("zebra", search.searchedQuery)
        assertFalse(search.inProgress)
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerSearch(3) }
    }

    @Test
    fun `the first match is scrolled to as soon as it is found`() = runTest(rule.dispatcher) {
        val viewModel = loaded()
        viewModel.events.test {
            viewModel.onSearchQueryChange("zebra")
            viewModel.submitSearch()
            assertEquals(PdfViewerUiEvent.ScrollToPage(1, a), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a query with no matches leaves nothing current`() {
        val viewModel = loaded(FakePdfDocumentSource())
        viewModel.onSearchQueryChange("zebra")
        viewModel.submitSearch()
        rule.advance()

        val search = viewModel.state.value.search
        assertTrue(search.matches.isEmpty())
        assertEquals(-1, search.currentIndex)
        assertEquals("zebra", search.searchedQuery)
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerSearch(0) }
    }

    @Test
    fun `a blank query searches nothing`() {
        val document = document()
        val viewModel = loaded(document)
        viewModel.onSearchQueryChange("   ")
        viewModel.submitSearch()
        rule.advance()

        assertTrue(document.searchedPages.isEmpty())
        assertNull(viewModel.state.value.search.searchedQuery)
    }

    @Test
    fun `next and previous walk the matches and wrap around both ends`() = runTest(rule.dispatcher) {
        val viewModel = loaded()
        viewModel.onSearchQueryChange("zebra")
        viewModel.submitSearch()
        rule.advance()

        viewModel.events.test {
            viewModel.nextMatch()
            assertEquals(PdfViewerUiEvent.ScrollToPage(1, b), awaitItem())
            viewModel.nextMatch()
            assertEquals(PdfViewerUiEvent.ScrollToPage(3, c), awaitItem())
            viewModel.nextMatch()
            assertEquals(PdfViewerUiEvent.ScrollToPage(1, a), awaitItem())
            assertEquals(0, viewModel.state.value.search.currentIndex)

            viewModel.previousMatch()
            assertEquals(PdfViewerUiEvent.ScrollToPage(3, c), awaitItem())
            assertEquals(2, viewModel.state.value.search.currentIndex)
        }
    }

    @Test
    fun `a new search stops the old one between pages and keeps none of its matches`() {
        val document = document()
        val viewModel = loaded(document)
        document.onSearch = { page, query ->
            // Mid-way through the first search, the user searches again.
            if (query == "zebra" && page == 1) {
                viewModel.onSearchQueryChange("okapi")
                viewModel.submitSearch()
            }
        }
        viewModel.onSearchQueryChange("zebra")
        viewModel.submitSearch()
        rule.advance()

        val zebraPages = document.searchedPages.filter { it.second == "zebra" }.map { it.first }
        assertEquals("the old search must stop after the page it was on", listOf(0, 1), zebraPages)
        val okapiPages = document.searchedPages.filter { it.second == "okapi" }.map { it.first }
        assertEquals(listOf(0, 1, 2, 3), okapiPages)
        val search = viewModel.state.value.search
        assertEquals("okapi", search.searchedQuery)
        // The fake answers every query alike, so the old search's page-1 matches, had they landed,
        // would show up here twice.
        assertEquals(3, search.matches.size)
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerSearch(any()) }
    }

    @Test
    fun `closing search stops it and clears every result`() {
        val document = document()
        val viewModel = loaded(document)
        document.onSearch = { page, _ -> if (page == 0) viewModel.closeSearch() }
        viewModel.onSearchQueryChange("zebra")
        viewModel.submitSearch()
        rule.advance()

        assertEquals(listOf(0), document.searchedPages.map { it.first })
        assertEquals(PdfSearchState(), viewModel.state.value.search)
        verify(exactly = 0) { AnalyticsTracker.trackPdfViewerSearch(any()) }
    }

    @Test
    fun `view-only has no search`() {
        val document = document()
        val viewModel = loaded(document, PdfViewerMode.VIEW_ONLY)
        viewModel.onSearchQueryChange("zebra")
        viewModel.submitSearch()
        rule.advance()

        assertFalse(viewModel.state.value.search.active)
        assertTrue(document.searchedPages.isEmpty())
    }

    @Test
    fun `search cannot open before the document has loaded`() {
        val viewModel = rule.viewModel(FakePdfDocumentOpener(password = "secret"))
        rule.advance()
        viewModel.openSearch()

        assertFalse(viewModel.state.value.search.active)
    }
}
