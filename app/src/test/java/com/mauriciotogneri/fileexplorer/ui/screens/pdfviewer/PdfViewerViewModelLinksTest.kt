package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.PdfLink
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelLinksTest {

    @get:Rule
    val rule = PdfViewerTestRule()

    private val top = PdfRectPt(10f, 10f, 90f, 30f)
    private val middle = PdfRectPt(10f, 40f, 90f, 60f)
    private val bottom = PdfRectPt(10f, 70f, 90f, 90f)

    private fun loaded(links: List<PdfLink>, mode: PdfViewerMode = PdfViewerMode.FULL): Pair<PdfViewerViewModel, FakePdfDocumentSource> {
        val document = FakePdfDocumentSource(
            pageSizes = List(3) { PdfPageSize(100, 100) },
            linksByPage = mapOf(0 to links)
        )
        val viewModel = rule.viewModel(FakePdfDocumentOpener(mode = mode, document = document))
        rule.advance()
        return viewModel to document
    }

    @Test
    fun `tapping a link to another page scrolls there`() = runTest(rule.dispatcher) {
        val (viewModel, _) = loaded(listOf(PdfLink.GoTo(2, listOf(top))))

        viewModel.events.test {
            viewModel.onPageTapped(0, 50f, 20f)
            assertEquals(PdfViewerUiEvent.ScrollToPage(2), awaitItem())
        }
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerLinkOpened("internal") }
    }

    @Test
    fun `tapping outside every link does nothing`() = runTest(rule.dispatcher) {
        val (viewModel, _) = loaded(listOf(PdfLink.GoTo(2, listOf(top))))

        viewModel.events.test {
            viewModel.onPageTapped(0, 50f, 50f)
            viewModel.onPageTapped(0, 95f, 20f)
            rule.advance()
            expectNoEvents()
        }
        verify(exactly = 0) { AnalyticsTracker.trackPdfViewerLinkOpened(any()) }
    }

    @Test
    fun `a link to a page the document does not have is ignored`() = runTest(rule.dispatcher) {
        val (viewModel, _) = loaded(listOf(PdfLink.GoTo(3, listOf(top)), PdfLink.GoTo(-1, listOf(middle))))

        viewModel.events.test {
            viewModel.onPageTapped(0, 50f, 20f)
            viewModel.onPageTapped(0, 50f, 50f)
            rule.advance()
            expectNoEvents()
        }
    }

    @Test
    fun `web and mail links are opened outside the app`() = runTest(rule.dispatcher) {
        val (viewModel, _) = loaded(
            listOf(
                PdfLink.External("https://example.com/a", listOf(top)),
                PdfLink.External("mailto:someone@example.com", listOf(middle))
            )
        )

        viewModel.events.test {
            viewModel.onPageTapped(0, 50f, 20f)
            assertEquals(PdfViewerUiEvent.OpenExternalLink("https://example.com/a"), awaitItem())
            viewModel.onPageTapped(0, 50f, 50f)
            assertEquals(PdfViewerUiEvent.OpenExternalLink("mailto:someone@example.com"), awaitItem())
        }
        verify(exactly = 2) { AnalyticsTracker.trackPdfViewerLinkOpened("external") }
    }

    @Test
    fun `script, file and intent links are dropped`() = runTest(rule.dispatcher) {
        val (viewModel, _) = loaded(
            listOf(
                PdfLink.External("javascript:alert(1)", listOf(top)),
                PdfLink.External("file:///sdcard/Download/other.pdf", listOf(middle)),
                PdfLink.External("intent://scan/#Intent;scheme=zxing;end", listOf(bottom))
            )
        )

        viewModel.events.test {
            viewModel.onPageTapped(0, 50f, 20f)
            viewModel.onPageTapped(0, 50f, 50f)
            viewModel.onPageTapped(0, 50f, 80f)
            rule.advance()
            expectNoEvents()
        }
        verify(exactly = 0) { AnalyticsTracker.trackPdfViewerLinkOpened(any()) }
    }

    @Test
    fun `a page's links are read once and reused`() {
        val (viewModel, document) = loaded(listOf(PdfLink.GoTo(2, listOf(top))))

        viewModel.onPageTapped(0, 50f, 50f)
        viewModel.onPageTapped(0, 50f, 50f)
        rule.advance()

        assertEquals(listOf(0), document.linkRequests)
    }

    @Test
    fun `view-only never asks the renderer for links`() {
        val (viewModel, document) = loaded(listOf(PdfLink.GoTo(2, listOf(top))), PdfViewerMode.VIEW_ONLY)

        viewModel.onPageTapped(0, 50f, 20f)
        rule.advance()

        assertTrue(document.linkRequests.isEmpty())
    }
}
