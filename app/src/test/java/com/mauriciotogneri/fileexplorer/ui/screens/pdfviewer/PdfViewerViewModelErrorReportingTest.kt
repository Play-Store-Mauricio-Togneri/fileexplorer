package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import com.mauriciotogneri.fileexplorer.data.util.ThumbnailFileType
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Which [PdfViewerViewModel] failures reach Crashlytics, and that what does reach it — or
 * Analytics — never says which file it was.
 *
 * The expected, unactionable failures of a PDF (see `isUnreadablePdf`) stay out, so the reports
 * that remain are bugs; each suppression is pinned together with a case that must still be
 * reported, since without its twin deleting the guard would leave the suite green.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelErrorReportingTest {

    @get:Rule
    val rule = PdfViewerTestRule()

    @Test
    fun `a missing or corrupted file is shown but not reported`() {
        listOf(
            FileNotFoundException("${PdfViewerTestRule.FILE_PATH}: open failed: ENOENT"),
            IOException("file not in PDF format or corrupted"),
            IllegalArgumentException("file descriptor not seekable")
        ).forEach { failure ->
            val viewModel = rule.viewModel(FakePdfDocumentOpener(failure = failure))
            rule.advance()
            assertEquals("$failure", PdfViewerContent.LoadError, viewModel.state.value.content)
        }

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 3) { AnalyticsTracker.trackPdfViewerLoadError(PdfViewerTestRule.SOURCE, "unreadable") }
    }

    @Test
    fun `an unexpected open failure is reported, without the file's identity`() {
        val reported = slot<Throwable>()
        val viewModel = rule.viewModel(
            FakePdfDocumentOpener(failure = UnsupportedOperationException("failed on ${PdfViewerTestRule.FILE_PATH}"))
        )
        rule.advance()

        assertEquals(PdfViewerContent.LoadError, viewModel.state.value.content)
        verify(exactly = 1) { ErrorReporter.warning(capture(reported), "pdf_viewer_open", ThumbnailFileType.PDF) }
        verify(exactly = 1) { AnalyticsTracker.trackPdfViewerLoadError(PdfViewerTestRule.SOURCE, "error") }
        assertNoFileIdentity(reported.captured)
    }

    @Test
    fun `an encrypted document is never reported, in either mode`() {
        for (mode in PdfViewerMode.entries) {
            rule.viewModel(FakePdfDocumentOpener(mode = mode, password = "secret"))
            rule.advance()
        }

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `a page that cannot be drawn is not reported, a render bug is`() = runTest(rule.dispatcher) {
        val document = FakePdfDocumentSource(
            pageSizes = List(2) { PdfPageSize(100, 100) },
            failingPages = mapOf(
                0 to IllegalStateException("cannot load page"),
                1 to ArithmeticException("bug in ${PdfViewerTestRule.FILE_NAME}")
            )
        )
        val viewModel = rule.viewModel(FakePdfDocumentOpener(document = document))
        rule.advance()
        verify(exactly = 1) { ErrorReporter.warning(any(), "pdf_viewer_page_size", any()) }

        viewModel.pageBitmap(0, 100)
        verify(exactly = 0) { ErrorReporter.warning(any(), "pdf_viewer_render", any()) }

        val reported = slot<Throwable>()
        viewModel.pageBitmap(1, 100)
        verify(exactly = 1) { ErrorReporter.warning(capture(reported), "pdf_viewer_render", ThumbnailFileType.PDF) }
        assertNoFileIdentity(reported.captured)
    }

    @Test
    fun `a search failure is reported only when it is not a bad page`() {
        val quiet = rule.viewModel(
            FakePdfDocumentOpener(document = FakePdfDocumentSource(searchFailure = IllegalStateException("cannot load page")))
        )
        rule.advance()
        quiet.onSearchQueryChange("zebra")
        quiet.submitSearch()
        rule.advance()
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }

        val loud = rule.viewModel(
            FakePdfDocumentOpener(document = FakePdfDocumentSource(searchFailure = ArithmeticException("bug")))
        )
        rule.advance()
        loud.onSearchQueryChange("zebra")
        loud.submitSearch()
        rule.advance()
        verify(exactly = 3) { ErrorReporter.warning(any(), "pdf_viewer_search", ThumbnailFileType.PDF) }
    }

    @Test
    fun `analytics never carry the file name, the path or the search query`() {
        val viewModel = rule.viewModel(FakePdfDocumentOpener())
        rule.advance()
        viewModel.onSearchQueryChange("zebra")
        viewModel.submitSearch()
        rule.advance()

        val values = mutableListOf<String>()
        verify { AnalyticsTracker.trackFileOpened(capture(values), capture(values), capture(values)) }
        verify { AnalyticsTracker.trackPdfViewerOpened(capture(values), capture(values)) }
        verify { AnalyticsTracker.trackPdfViewerSearch(any()) }
        values.forEach { value ->
            assertFalse(value, value.contains("Quarterly"))
            assertFalse(value, value.contains("/storage"))
            assertFalse(value, value.contains("zebra"))
        }
    }

    private fun assertNoFileIdentity(throwable: Throwable) {
        val text = generateSequence(throwable) { it.cause }.joinToString { "${it.message}" }
        assertFalse(text, text.contains("Quarterly"))
        assertFalse(text, text.contains("/storage"))
    }
}
