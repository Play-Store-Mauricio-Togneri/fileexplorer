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
import org.junit.Assert.assertNull
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
    fun `the artwork key is published once the modification time has been read`() {
        val viewModel = rule.viewModel(playback)
        assertNull(viewModel.state.value.artworkCacheKey)

        rule.runCurrent()

        // The file is never created, so its modification time reads as 0.
        assertEquals("${MediaViewerTestRule.AUDIO_PATH}:0", viewModel.state.value.artworkCacheKey)
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
    fun `a length the player learns after ready replaces the unknown one`() {
        val viewModel = ready(durationMs = null)
        assertEquals(MediaViewerContent.Ready, viewModel.state.value.content)
        assertNull(viewModel.state.value.durationMs)

        playback.listener.onDurationChanged(durationMs = 42_000)

        assertEquals(42_000L, viewModel.state.value.durationMs)
    }

    @Test
    fun `a length learned after a failure does not change the failed state`() {
        val viewModel = rule.viewModel(playback)
        playback.listener.onError(RuntimeException("broken"), expected = true)

        playback.listener.onDurationChanged(durationMs = 42_000)

        assertNull(viewModel.state.value.durationMs)
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

    @Test
    fun `the title and artist come from the file's tags`() {
        val viewModel = rule.viewModel(playback)

        playback.listener.onMetadataChanged(title = " Dance Monkey ", artist = "Tones and I")

        assertEquals("Dance Monkey", viewModel.state.value.title)
        assertEquals("Tones and I", viewModel.state.value.artist)
    }

    @Test
    fun `blank tags count as none`() {
        val viewModel = rule.viewModel(playback)

        playback.listener.onMetadataChanged(title = "  ", artist = "")

        assertNull(viewModel.state.value.title)
        assertNull(viewModel.state.value.artist)
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
    fun `the end of a file of unknown length keeps the position playback reached`() {
        val viewModel = ready(durationMs = null)
        playback.positionMs = 83_000
        playback.listener.onEnded()

        assertEquals(83_000L, viewModel.state.value.positionMs)
    }

    @Test
    fun `seeking in a file of unknown length is not capped at zero`() {
        val viewModel = ready(durationMs = null)

        viewModel.seekTo(4_000)
        viewModel.seekTo(-1)

        assertEquals(listOf(4_000L, 0L), playback.seeks)
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

    @Test
    fun `the sound starts on, and muting leaves playback going`() {
        val viewModel = ready()
        assertFalse(viewModel.state.value.muted)

        viewModel.toggleMute()
        assertTrue(viewModel.state.value.muted)
        assertTrue(viewModel.state.value.playing)

        viewModel.toggleMute()
        assertFalse(viewModel.state.value.muted)
        assertEquals(listOf(true, false), playback.mutes)
        assertEquals(0, playback.pauseCount)
    }

    @Test
    fun `muting does nothing before the file is ready`() {
        val viewModel = rule.viewModel(playback)
        rule.runCurrent()

        viewModel.toggleMute()

        assertFalse(viewModel.state.value.muted)
        assertEquals(emptyList<Boolean>(), playback.mutes)
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
    fun `decoders reclaimed in the background are reloaded on coming back, still paused`() {
        val viewModel = ready()
        viewModel.onStop()

        playback.listener.onReclaimed(RuntimeException("reclaimed"))
        assertEquals(0, playback.reloadCount)

        viewModel.onStart()

        assertEquals(1, playback.reloadCount)
        assertEquals(MediaViewerContent.Ready, viewModel.state.value.content)
        assertFalse(viewModel.state.value.playing)
        assertEquals(1, playback.playCount)
    }

    @Test
    fun `decoders reclaimed while visible are reloaded at once`() {
        val viewModel = ready()

        playback.listener.onReclaimed(RuntimeException("reclaimed"))

        assertEquals(1, playback.reloadCount)
        assertEquals(MediaViewerContent.Ready, viewModel.state.value.content)
    }

    @Test
    fun `decoders reclaimed again in the same visit fail instead of reloading`() {
        val viewModel = ready()

        playback.listener.onReclaimed(RuntimeException("first"))
        playback.listener.onReclaimed(RuntimeException("second"))

        assertEquals(1, playback.reloadCount)
        assertEquals(MediaViewerContent.LoadError, viewModel.state.value.content)
    }

    @Test
    fun `every visit gets its own reload`() {
        val viewModel = ready()
        playback.listener.onReclaimed(RuntimeException("first"))
        viewModel.onStop()
        viewModel.onStart()

        playback.listener.onReclaimed(RuntimeException("second"))

        assertEquals(2, playback.reloadCount)
        assertEquals(MediaViewerContent.Ready, viewModel.state.value.content)
    }

    @Test
    fun `starting without having stopped does not grant another reload`() {
        val viewModel = ready()
        playback.listener.onReclaimed(RuntimeException("first"))

        // What a rotation does: the screen starts again without the view model having stopped.
        viewModel.onStart()
        playback.listener.onReclaimed(RuntimeException("second"))

        assertEquals(1, playback.reloadCount)
        assertEquals(MediaViewerContent.LoadError, viewModel.state.value.content)
    }

    @Test
    fun `clearing the view model releases the player once`() {
        val viewModel = ready()
        clear(viewModel)
        rule.advanceTimeBy(MediaViewerViewModel.PROGRESS_INTERVAL_MS * 4)

        assertEquals(1, playback.releaseCount)
        assertEquals(0, playback.callsAfterRelease)
    }

    private fun ready(durationMs: Long? = 10_000): MediaViewerViewModel {
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
