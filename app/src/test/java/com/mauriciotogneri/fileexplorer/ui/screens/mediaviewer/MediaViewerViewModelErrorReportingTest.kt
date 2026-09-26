package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import com.mauriciotogneri.fileexplorer.data.source.FakeMediaPlayback
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.ThumbnailFileType
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Which [MediaViewerViewModel] failures reach Crashlytics, and that what does reach it never says
 * which file it was. Each suppression is pinned together with a case that must still be reported.
 */
class MediaViewerViewModelErrorReportingTest {

    @get:Rule
    val rule = MediaViewerTestRule()

    private val playback = FakeMediaPlayback()

    @Test
    fun `an unplayable file is shown but not reported`() {
        val viewModel = rule.viewModel(playback)
        playback.listener.onError(IllegalStateException("unsupported"), expected = true)
        rule.runCurrent()

        assertEquals(MediaViewerContent.LoadError, viewModel.state.value.content)
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackMediaViewerLoadError(MediaViewerTestRule.SOURCE, "unplayable") }
    }

    @Test
    fun `an unexpected audio failure is reported, without the file's identity`() {
        val reported = slot<Throwable>()
        val viewModel = rule.viewModel(playback)
        playback.listener.onError(
            RuntimeException("failed on file://${MediaViewerTestRule.AUDIO_PATH}"),
            expected = false
        )
        rule.runCurrent()

        assertEquals(MediaViewerContent.LoadError, viewModel.state.value.content)
        verify(exactly = 1) {
            ErrorReporter.warning(capture(reported), "media_viewer_play", ThumbnailFileType.AUDIO)
        }
        verify(exactly = 1) { AnalyticsTracker.trackMediaViewerLoadError(MediaViewerTestRule.SOURCE, "error") }
        assertFalse(reported.captured.toString().contains("Private voice memo"))
        assertEquals(null, reported.captured.cause)
    }

    @Test
    fun `an unexpected video failure is reported as a video`() {
        rule.viewModel(playback, filePath = MediaViewerTestRule.VIDEO_PATH)
        playback.listener.onError(RuntimeException("boom"), expected = false)
        rule.runCurrent()

        verify(exactly = 1) { ErrorReporter.warning(any(), "media_viewer_play", ThumbnailFileType.VIDEO) }
    }

    @Test
    fun `only the first failure is counted, and a later ready does not hide it`() {
        val viewModel = rule.viewModel(playback)
        playback.listener.onError(RuntimeException("first"), expected = false)
        playback.listener.onError(RuntimeException("second"), expected = false)
        playback.listener.onReady(durationMs = 1_000)
        rule.runCurrent()

        assertEquals(MediaViewerContent.LoadError, viewModel.state.value.content)
        verify(exactly = 1) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackMediaViewerLoadError(any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackMediaViewerOpened(any(), any()) }
    }
}
