package com.mauriciotogneri.fileexplorer.ui.screens.imageviewer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository

    private val testPath = "/storage/emulated/0/Pictures/photo.jpg"
    private val testSource = "folder"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackImageViewerLoadError(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
    }

    @Test
    fun `state exposes the file name of the path being viewed`() {
        assertEquals("photo.jpg", createViewModel().state.value.fileName)
    }

    @Test
    fun `a file that can no longer be read is not reported`() {
        // The exact failure reported by Crashlytics: Coil's decoder sniff is the first thing to open
        // the file and finds it gone — deleted or its volume unmounted while the viewer was on screen.
        // Environmental, and the error UI already covers it.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(FileNotFoundException("$testPath: open failed: ENOENT"))

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `content no decoder can turn into a bitmap is not reported`() {
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("BitmapFactory returned a null bitmap"))

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `a corrupt animated file is not reported`() {
        // Below API 28 Coil's GifDecoder raises this instead of the IOException ImageDecoder raises
        // from API 28; the same bad file must stay quiet on either.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("Failed to decode GIF."))

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `a load failure for any other reason is still reported`() {
        // Keeps the suppressions above from widening: anything that is neither an unreadable file nor
        // undecodable content is a candidate app bug and has to reach Crashlytics.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("boom"))

        verify(exactly = 1) { ErrorReporter.warning(any(), "image_viewer_load", any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `a load failure without a throwable is counted but not reported`() {
        val viewModel = createViewModel()

        viewModel.onImageLoadError(null)

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `only the first load failure of a viewing is reported`() {
        // Coil re-invokes the error slot across recompositions; one viewing must not multiply into a
        // burst of identical reports.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("boom"))
        viewModel.onImageLoadError(IllegalStateException("boom"))

        verify(exactly = 1) { ErrorReporter.warning(any(), "image_viewer_load", any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    private fun createViewModel() = ImageViewerViewModel(
        filePath = testPath,
        source = testSource,
        application = application,
        fileRepository = fileRepository,
        ioDispatcher = testDispatcher
    )
}
