package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.mauriciotogneri.fileexplorer.data.source.FakeMediaPlayback
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class MediaViewerViewModelTest {

    @get:Rule
    val rule = MediaViewerTestRule()

    private val playback = FakeMediaPlayback()

    // ==================== Opening ====================

    @Test
    fun `opening loads the file and starts playing`() {
        val viewModel = rule.viewModel(playback)
        rule.runCurrent()

        assertEquals(listOf(File(MediaViewerTestRule.AUDIO_PATH)), playback.openedFiles)
        assertEquals(1, playback.playCount)
        assertEquals("Private voice memo.mp3", viewModel.state.value.fileName)
        assertEquals(MediaViewerContent.Loading, viewModel.state.value.content)
        assertTrue(viewModel.state.value.playing)
    }

    @Test
    fun `ready shows the controls with the duration, and tracks the open once`() {
        val viewModel = rule.viewModel(playback)
        playback.listener.onReady(durationMs = 90_000)
        rule.runCurrent()
        // A seek that has to buffer reports ready again: still one open.
        playback.listener.onReady(durationMs = 90_000)
        rule.runCurrent()

        assertEquals(MediaViewerContent.Ready, viewModel.state.value.content)
        assertEquals(90_000L, viewModel.state.value.durationMs)
        verify(exactly = 1) { IntentUtil.trackRecentFile(any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackFileOpened("mp3", any(), MediaViewerTestRule.SOURCE) }
        verify(exactly = 1) { AnalyticsTracker.trackMediaViewerOpened(MediaViewerTestRule.SOURCE, "audio") }
    }

    @Test
    fun `a file with video frames is tracked as a video`() {
        rule.viewModel(playback, filePath = MediaViewerTestRule.VIDEO_PATH)
        playback.listener.onVideoChanged(hasVideo = true)
        playback.listener.onReady(durationMs = 1_000)
        rule.runCurrent()

        verify(exactly = 1) { AnalyticsTracker.trackMediaViewerOpened(MediaViewerTestRule.SOURCE, "video") }
    }

    @Test
    fun `a file that never gets ready is not tracked as opened`() {
        rule.viewModel(playback)
        rule.runCurrent()

        verify(exactly = 0) { IntentUtil.trackRecentFile(any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackFileOpened(any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackMediaViewerOpened(any(), any()) }
    }

    // ==================== Controls ====================

    @Test
    fun `play and pause follow the button`() {
        val viewModel = ready()

        viewModel.togglePlay()
        assertFalse(viewModel.state.value.playing)
        assertEquals(1, playback.pauseCount)

        viewModel.togglePlay()
        assertTrue(viewModel.state.value.playing)
        assertEquals(2, playback.playCount)
    }

    @Test
    fun `controls do nothing before the file is ready`() {
        val viewModel = rule.viewModel(playback)
        rule.runCurrent()

        viewModel.togglePlay()
        viewModel.seekTo(5_000)

        assertEquals(0, playback.pauseCount)
        assertEquals(emptyList<Long>(), playback.seeks)
    }

    @Test
    fun `play at the end starts over`() {
        val viewModel = ready(durationMs = 10_000)
        playback.positionMs = 10_000
        playback.listener.onEnded()
        playback.pause()
        rule.runCurrent()
        assertEquals(10_000L, viewModel.state.value.positionMs)

        viewModel.togglePlay()

        assertEquals(listOf(0L), playback.seeks)
        assertEquals(0L, viewModel.state.value.positionMs)
        assertTrue(viewModel.state.value.playing)
    }

    @Test
    fun `play after pausing mid-file resumes where it was`() {
        val viewModel = ready(durationMs = 10_000)
        viewModel.togglePlay()
        viewModel.togglePlay()

        assertEquals(emptyList<Long>(), playback.seeks)
    }

    @Test
    fun `seeking stays within the file`() {
        val viewModel = ready(durationMs = 10_000)

        viewModel.seekTo(4_000)
        assertEquals(4_000L, viewModel.state.value.positionMs)
        viewModel.seekTo(-1)
        viewModel.seekTo(60_000)

        assertEquals(listOf(4_000L, 0L, 10_000L), playback.seeks)
        assertEquals(10_000L, viewModel.state.value.positionMs)
    }

    @Test
    fun `seeking back after the end does not restart on play`() {
        val viewModel = ready(durationMs = 10_000)
        playback.listener.onEnded()
        playback.pause()
        viewModel.seekTo(3_000)

        viewModel.togglePlay()

        assertEquals(listOf(3_000L), playback.seeks)
    }

    // ==================== Progress ====================

    @Test
    fun `the position follows playback only while playing`() {
        val viewModel = ready(durationMs = 10_000)

        playback.positionMs = 1_000
        rule.advanceTimeBy(MediaViewerViewModel.PROGRESS_INTERVAL_MS)
        assertEquals(1_000L, viewModel.state.value.positionMs)

        viewModel.togglePlay()
        playback.positionMs = 2_000
        rule.advanceTimeBy(MediaViewerViewModel.PROGRESS_INTERVAL_MS * 4)
        assertEquals(1_000L, viewModel.state.value.positionMs)
    }

    // ==================== Fullscreen ====================

    @Test
    fun `fullscreen is only for video`() {
        val viewModel = ready()

        viewModel.toggleFullscreen()
        assertFalse(viewModel.state.value.fullscreen)

        playback.listener.onVideoChanged(hasVideo = true)
        viewModel.toggleFullscreen()
        assertTrue(viewModel.state.value.fullscreen)

        viewModel.toggleFullscreen()
        assertFalse(viewModel.state.value.fullscreen)
    }

    @Test
    fun `losing the video leaves fullscreen`() {
        val viewModel = ready()
        playback.listener.onVideoChanged(hasVideo = true)
        viewModel.toggleFullscreen()

        playback.listener.onVideoChanged(hasVideo = false)

        assertFalse(viewModel.state.value.fullscreen)
    }

    @Test
    fun `a playback error leaves fullscreen`() {
        val viewModel = ready()
        playback.listener.onVideoChanged(hasVideo = true)
        viewModel.toggleFullscreen()

        playback.listener.onError(IllegalStateException("decoder"), expected = true)

        assertFalse(viewModel.state.value.fullscreen)
        assertFalse(viewModel.state.value.playing)
    }

    @Test
    fun `exiting fullscreen leaves it`() {
        val viewModel = ready()
        playback.listener.onVideoChanged(hasVideo = true)
        viewModel.toggleFullscreen()

        viewModel.exitFullscreen()

        assertFalse(viewModel.state.value.fullscreen)
    }

    // ==================== Lifecycle ====================

    @Test
    fun `leaving the screen pauses, and coming back does not resume`() {
        val viewModel = ready()

        viewModel.onStop()

        assertFalse(viewModel.state.value.playing)
        assertEquals(1, playback.pauseCount)
        assertEquals(1, playback.playCount)
    }

    @Test
    fun `clearing the view model releases the player once`() {
        val viewModel = ready()
        clear(viewModel)
        rule.advanceTimeBy(MediaViewerViewModel.PROGRESS_INTERVAL_MS * 4)

        assertEquals(1, playback.releaseCount)
        assertEquals(0, playback.callsAfterRelease)
    }

    private fun ready(durationMs: Long = 10_000): MediaViewerViewModel {
        val viewModel = rule.viewModel(playback)
        playback.listener.onReady(durationMs)
        rule.runCurrent()
        return viewModel
    }

    /** Clears [viewModel] the way the framework does: through the store that owns it. */
    private fun clear(viewModel: MediaViewerViewModel) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        ViewModelProvider(store, factory)[MediaViewerViewModel::class.java]
        store.clear()
    }
}
