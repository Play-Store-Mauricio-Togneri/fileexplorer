package com.mauriciotogneri.fileexplorer.ui.screens.textviewer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.TextFilePreview
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Which read failures [TextViewerViewModel] hands to Crashlytics.
 *
 * The rest of the screen is covered by the instrumentation `TextViewerViewModelTest`; this one is a
 * JVM test because the failure path never reaches Android. `loadContent` builds its pair with
 * `TextFilePreview.read(...) to FileItem.from(...)`, and Kotlin evaluates the receiver first, so a
 * throw from the read — pure `java.io` — short-circuits every framework call the success path makes.
 *
 * Both cases have to be pinned together: with only the first, inverting or deleting the guard leaves
 * the suite green, and the branch decides whether a real bug is visible in production.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextViewerViewModelErrorReportingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository

    // Deliberately never created: opening it is what raises FileNotFoundException.
    private val missingPath = "/storage/emulated/0/Documents/absent_${System.nanoTime()}.md"
    private val testSource = "folder"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        // A MockK object mock is a spy: the call left unstubbed below runs the real reader.
        mockkObject(TextFilePreview)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackTextViewerReadError(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
        unmockkObject(TextFilePreview)
    }

    @Test
    fun `a text file that can no longer be read is not reported`() = runTest {
        // The exact failure reported by Crashlytics: the file was listed, then deleted or its volume
        // unmounted before the viewer opened it. Environmental, and the error UI already covers it.
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackTextViewerReadError(testSource) }
        assertTrue("The user still has to be told the read failed", viewModel.state.value.error)
    }

    @Test
    fun `a read failure for any other reason is still reported`() = runTest {
        // Keeps the suppression above from widening: a defect in the app's own handling of bytes it
        // did read is never an IOException, and has to stay visible in Crashlytics.
        every { TextFilePreview.read(any(), any(), any(), any()) } throws
            StringIndexOutOfBoundsException("length=3; index=7")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { ErrorReporter.warning(any(), "text_viewer_read", any()) }
        verify(exactly = 1) { AnalyticsTracker.trackTextViewerReadError(testSource) }
        assertTrue(viewModel.state.value.error)
    }

    private fun createViewModel() = TextViewerViewModel(
        filePath = missingPath,
        source = testSource,
        application = application,
        fileRepository = fileRepository,
        ioDispatcher = testDispatcher
    )
}
