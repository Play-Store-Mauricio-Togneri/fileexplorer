package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import android.app.Application
import android.graphics.Bitmap
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Shared set-up for the [PdfViewerViewModel] tests: a test Main dispatcher, and the reporting
 * objects replaced by recorders so the tests can verify what was — and was not — sent.
 *
 * Only a whole-object spy can stand in for `AnalyticsTracker` and `ErrorReporter`, so every call is
 * stubbed rather than left to the real, Firebase-backed implementation. `IntentUtil.trackRecentFile`
 * is stubbed too: the real one launches onto its own IO scope and would report the mocked
 * Application's missing DataStore as a warning at an arbitrary later point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerTestRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    val application: Application = mockk(relaxed = true)
    val fileRepository: FileRepository = mockk(relaxed = true)

    /** Distinct per rendered size, so a test can tell which render a caller was handed. */
    val bitmapFactory: (Int, Int) -> Bitmap = { _, _ -> mockk(relaxed = true) }

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
        mockkObject(ErrorReporter, AnalyticsTracker, IntentUtil)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.setCount(any(), any()) } just Runs
        every { ErrorReporter.recordHeap() } just Runs
        every { AnalyticsTracker.trackFileOpened(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackPdfViewerOpened(any(), any()) } just Runs
        every { AnalyticsTracker.trackPdfViewerLoadError(any(), any()) } just Runs
        every { AnalyticsTracker.trackPdfViewerSearch(any()) } just Runs
        every { AnalyticsTracker.trackPdfViewerLinkOpened(any()) } just Runs
        every { AnalyticsTracker.trackPdfViewerShare(any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter, AnalyticsTracker, IntentUtil)
    }

    fun viewModel(
        opener: FakePdfDocumentOpener,
        filePath: String = FILE_PATH,
        rendererDispatcher: CoroutineDispatcher = dispatcher,
        cacheBudgetBytes: Long = Long.MAX_VALUE
    ) = PdfViewerViewModel(
        filePath = filePath,
        source = SOURCE,
        application = application,
        fileRepository = fileRepository,
        opener = opener,
        ioDispatcher = dispatcher,
        rendererDispatcher = rendererDispatcher,
        bitmapFactory = bitmapFactory,
        cacheBudgetBytes = cacheBudgetBytes
    )

    fun advance() = dispatcher.scheduler.advanceUntilIdle()

    companion object {
        /** Never created: the viewer reads the document only through the fake opener. */
        const val FILE_PATH = "/storage/emulated/0/Documents/Quarterly private report.pdf"
        const val FILE_NAME = "Quarterly private report.pdf"
        const val SOURCE = "folder"
    }
}
