package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerSupport
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelTest {

    @get:Rule
    val rule = PdfViewerTestRule()

    // ==================== Loading ====================

    @Test
    fun `state names the file and the mode before anything loads`() {
        val viewModel = rule.viewModel(FakePdfDocumentOpener(mode = PdfViewerMode.VIEW_ONLY))

        assertEquals(PdfViewerTestRule.FILE_NAME, viewModel.state.value.fileName)
        assertEquals(PdfViewerMode.VIEW_ONLY, viewModel.state.value.mode)
        assertEquals(PdfViewerContent.Loading, viewModel.state.value.content)
    }

    @Test
    fun `a document loads with every page size`() {
        val sizes = listOf(PdfPageSize(100, 200), PdfPageSize(300, 150))
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = FakePdfDocumentSource(sizes)))
        rule.advance()

        assertEquals(PdfViewerContent.Loaded(sizes), viewModel.state.value.content)
    }

    @Test
    fun `a successful open is tracked exactly once, after the load`() {
        val viewModel = rule.viewModel(FakePdfDocumentOpener())
        rule.advance()
        // Nothing the user does afterwards may count the open again.
        viewModel.goToPage(1)
        viewModel.onPageTapped(0, 1f, 1f)
        rule.advance()

        verify(exactly = 1) { IntentUtil.trackRecentFile(any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackFileOpened("pdf", "application/pdf", PdfViewerTestRule.SOURCE) }
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerOpened(PdfViewerTestRule.SOURCE, "full") }
        verify(exactly = 0) { AnalyticsTracker.trackPdfViewerLoadError(any(), any()) }
    }

    @Test
    fun `the view-only mode is what the open event reports`() {
        rule.viewModel(FakePdfDocumentOpener(mode = PdfViewerMode.VIEW_ONLY))
        rule.advance()

        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerOpened(PdfViewerTestRule.SOURCE, "view_only") }
    }

    @Test
    fun `a failed load is neither a recent file nor an opened file`() {
        val viewModel = rule.viewModel(FakePdfDocumentOpener(failure = IOException("corrupted")))
        rule.advance()

        assertEquals(PdfViewerContent.LoadError, viewModel.state.value.content)
        verify(exactly = 0) { IntentUtil.trackRecentFile(any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackFileOpened(any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackPdfViewerOpened(any(), any()) }
    }

    @Test
    fun `a document with no pages is a load error`() {
        val empty = FakePdfDocumentSource(pageSizes = emptyList())
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = empty))
        rule.advance()

        assertEquals(PdfViewerContent.LoadError, viewModel.state.value.content)
        assertEquals("the empty document must not be left open", 1, empty.closeCount)
    }

    @Test
    fun `a page whose size cannot be read borrows its neighbour's instead of failing the document`() {
        val document = FakePdfDocumentSource(
            pageSizes = listOf(PdfPageSize(100, 200), PdfPageSize(1, 1)),
            failingPages = mapOf(1 to IllegalStateException("cannot load page"))
        )
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        assertEquals(
            PdfViewerContent.Loaded(listOf(PdfPageSize(100, 200), PdfPageSize(100, 200))),
            viewModel.state.value.content
        )
    }

    @Test
    fun `a page too tall to lay out is loaded cut to the tallest shape the list can hold`() {
        val document = FakePdfDocumentSource(pageSizes = listOf(PdfPageSize(1, 1000)))
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        assertEquals(
            PdfViewerContent.Loaded(listOf(PdfPageSize(1, PdfViewerSupport.MAX_PAGE_ASPECT))),
            viewModel.state.value.content
        )
    }

    // ==================== Password ====================

    @Test
    fun `an encrypted document asks for its password, flags a wrong one and opens with the right one`() {
        val opener = FakePdfDocumentOpener(password = "secret")
        val viewModel = rule.viewModel(opener)
        rule.advance()
        assertEquals(PdfViewerContent.PasswordRequired(wrongPassword = false), viewModel.state.value.content)

        viewModel.submitPassword("guess")
        rule.advance()
        assertEquals(PdfViewerContent.PasswordRequired(wrongPassword = true), viewModel.state.value.content)

        viewModel.submitPassword("secret")
        rule.advance()
        assertTrue(viewModel.state.value.content is PdfViewerContent.Loaded)
        assertEquals(listOf(null, "guess", "secret"), opener.passwordsTried)
        // Handed to the renderer, never kept.
        assertFalse(viewModel.state.value.toString().contains("secret"))
        verify(exactly = 0) { AnalyticsTracker.trackPdfViewerLoadError(any(), any()) }
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerOpened(any(), any()) }
    }

    @Test
    fun `cancelling the password prompt closes the viewer`() = runTest(rule.dispatcher) {
        val viewModel = rule.viewModel(FakePdfDocumentOpener(password = "secret"))
        rule.advance()

        viewModel.events.test {
            viewModel.cancelPassword()
            assertEquals(PdfViewerUiEvent.Finish, awaitItem())
        }
    }

    @Test
    fun `a password is ignored unless one was asked for`() {
        val opener = FakePdfDocumentOpener()
        val viewModel = rule.viewModel(opener)
        rule.advance()

        viewModel.submitPassword("anything")
        rule.advance()

        assertEquals(listOf<String?>(null), opener.passwordsTried)
    }

    @Test
    fun `view-only cannot unlock an encrypted document and says so instead of asking`() {
        val opener = FakePdfDocumentOpener(mode = PdfViewerMode.VIEW_ONLY, password = "secret")
        val viewModel = rule.viewModel(opener)
        rule.advance()

        assertEquals(PdfViewerContent.PasswordUnsupported, viewModel.state.value.content)
        verify(exactly = 1) {
            AnalyticsTracker.trackPdfViewerLoadError(PdfViewerTestRule.SOURCE, "password_unsupported")
        }
        viewModel.submitPassword("secret")
        rule.advance()
        assertEquals(listOf<String?>(null), opener.passwordsTried)
    }

    // ==================== Rendering ====================

    @Test
    fun `a page renders at the requested width and is served from the cache after`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource(listOf(PdfPageSize(200, 300)))
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        val first = viewModel.pageBitmap(0, 1000)
        val second = viewModel.pageBitmap(0, 1000)

        assertNotNull(first)
        assertSame(first, second)
        assertSame(first, viewModel.cachedPageBitmap(0, 1000))
        assertEquals(listOf(FakePdfDocumentSource.Render(0, 5f, 0f, 0f)), document.renders)
    }

    @Test
    fun `a page that cannot be drawn yields no bitmap and stays out of the cache`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource(
            pageSizes = listOf(PdfPageSize(200, 300)),
            failingPages = mapOf(0 to IllegalStateException("cannot load page"))
        )
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        assertNull(viewModel.pageBitmap(0, 1000))
        assertNull(viewModel.cachedPageBitmap(0, 1000))
    }

    @Test
    fun `the cache evicts the least recently used page once over budget`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource(List(3) { PdfPageSize(100, 100) })
        // Room for two 100 x 100 renders and not a third.
        val budget = PdfViewerSupport.byteCount(PdfViewerSupport.renderSize(100, 100, 100, 1f)) * 2
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document), cacheBudgetBytes = budget)
        rule.advance()

        viewModel.pageBitmap(0, 100)
        viewModel.pageBitmap(1, 100)
        viewModel.pageBitmap(0, 100) // page 0 is now the most recently used
        viewModel.pageBitmap(2, 100)

        assertNotNull(viewModel.cachedPageBitmap(0, 100))
        assertNull(viewModel.cachedPageBitmap(1, 100))
        assertNotNull(viewModel.cachedPageBitmap(2, 100))
    }

    @Test
    fun `a zoomed region renders only its own area at the zoomed density`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource(listOf(PdfPageSize(200, 300)))
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        // Page laid out 1000 px wide: 5 px per point. At 2x, 10 px per point, and the region's
        // top-left corner (100, 400) unzoomed is (200, 800) in the zoomed page.
        assertNotNull(viewModel.regionBitmap(0, 1000, 2f, 100f, 400f, 500f, 300f))

        assertEquals(listOf(FakePdfDocumentSource.Render(0, 10f, 200f, 800f)), document.renders)
    }

    @Test
    fun `zoom beyond the maximum renders at the maximum`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource(listOf(PdfPageSize(200, 300)))
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        viewModel.regionBitmap(0, 100, 50f, 0f, 0f, 10f, 10f)

        assertEquals(0.5f * PdfViewerSupport.MAX_ZOOM, document.renders.single().scale)
    }

    @Test
    fun `renderer calls never overlap, however many callers there are`() {
        // Real threads: the property only means something when callers can actually run at once.
        val document = FakePdfDocumentSource(List(8) { PdfPageSize(100, 100) }, renderDelayMs = 5)
        val viewModel = rule.viewModel(
            FakePdfDocumentOpener(document = document),
            rendererDispatcher = Dispatchers.IO.limitedParallelism(1)
        )
        runBlocking {
            withTimeout(10_000) {
                while (viewModel.state.value.content !is PdfViewerContent.Loaded) {
                    rule.advance()
                    Thread.sleep(5)
                }
            }
            (0 until 8).flatMap { page ->
                listOf(
                    async(Dispatchers.Default) { viewModel.pageBitmap(page, 100) },
                    async(Dispatchers.Default) { viewModel.regionBitmap(page, 100, 2f, 0f, 0f, 50f, 50f) }
                )
            }.awaitAll()
        }

        assertEquals(16, document.renders.size)
        assertEquals(1, document.maxConcurrentCalls.get())
    }

    // ==================== Navigation ====================

    @Test
    fun `go to page scrolls there and ignores pages that do not exist`() = runTest(rule.dispatcher) {
        val viewModel = rule.viewModel(FakePdfDocumentOpener())
        rule.advance()

        viewModel.events.test {
            viewModel.goToPage(3)
            viewModel.goToPage(-1)
            viewModel.goToPage(2)
            assertEquals(PdfViewerUiEvent.ScrollToPage(2), awaitItem())
            expectNoEvents()
        }
    }

    // ==================== Lifecycle ====================

    @Test
    fun `clearing the view model closes the document on the renderer`() {
        val document = FakePdfDocumentSource()
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        clear(viewModel)
        rule.advance()

        assertEquals(1, document.closeCount)
        assertEquals(0, document.callsAfterClose)
    }

    @Test
    fun `a render queued behind the close finds the document gone rather than using it`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource()
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()

        clear(viewModel)
        rule.advance()

        assertNull(viewModel.pageBitmap(0, 100))
        assertEquals(0, document.callsAfterClose)
    }

    /** Clears [viewModel] the way the framework does: through the store that owns it. */
    private fun clear(viewModel: PdfViewerViewModel) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        ViewModelProvider(store, factory)[PdfViewerViewModel::class.java]
        store.clear()
    }
}
