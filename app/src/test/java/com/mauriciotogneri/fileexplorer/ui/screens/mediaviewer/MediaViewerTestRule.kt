package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.source.FakeMediaPlayback
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Shared set-up for the [MediaViewerViewModel] tests: a test Main dispatcher, and the reporting
 * objects replaced by recorders so the tests can verify what was — and was not — sent.
 *
 * `IntentUtil.trackRecentFile` is stubbed too: the real one launches onto its own IO scope and
 * would report the mocked Application's missing DataStore as a warning at an arbitrary later point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerTestRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    val application: Application = mockk(relaxed = true)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
        mockkObject(ErrorReporter, AnalyticsTracker, IntentUtil)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackFileOpened(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackMediaViewerOpened(any(), any()) } just Runs
        every { AnalyticsTracker.trackMediaViewerLoadError(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter, AnalyticsTracker, IntentUtil)
    }

    fun viewModel(
        playback: FakeMediaPlayback,
        filePath: String = AUDIO_PATH
    ) = MediaViewerViewModel(
        filePath = filePath,
        source = SOURCE,
        application = application,
        playback = playback,
        ioDispatcher = dispatcher
    )

    /**
     * Runs what is due now. Not `advanceUntilIdle`: while playback is requested the position is
     * followed on a timer that never goes idle.
     */
    fun runCurrent() = dispatcher.scheduler.runCurrent()

    fun advanceTimeBy(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    companion object {
        /** Never created: the viewer reads the file only through the fake playback. */
        const val AUDIO_PATH = "/storage/emulated/0/Music/Private voice memo.mp3"
        const val VIDEO_PATH = "/storage/emulated/0/Movies/Private family video.mp4"
        const val SOURCE = "folder"
    }
}
