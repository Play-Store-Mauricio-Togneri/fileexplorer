package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.source.ExoMediaPlayback
import com.mauriciotogneri.fileexplorer.data.source.MediaPlayback
import com.mauriciotogneri.fileexplorer.data.util.MediaTimeFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [MediaViewerScreen] over a [MediaViewerViewModel] driven by [ScriptedPlayback], so readiness,
 * tracks and timing are the test's to decide; one test plays a real file through
 * [ExoMediaPlayback] to cover the error state. Decoding real files is covered by
 * `ViewerActivityLaunchTest`.
 */
@RunWith(AndroidJUnit4::class)
class MediaViewerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_media_viewer_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun audio_showsTheTimesAndNoFullscreen() {
        val playback = ScriptedPlayback()
        render(playback)
        onMain { playback.listener.onReady(durationMs = 83_000) }

        waitForText(time(positionMs = 0, durationMs = 83_000))
        composeTestRule.onNodeWithText(AUDIO_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_fullscreen_enter))
            .assertDoesNotExist()
    }

    @Test
    fun playButton_togglesPlayback() {
        val playback = ScriptedPlayback()
        render(playback)
        onMain { playback.listener.onReady(durationMs = 10_000) }

        // Playback starts on open, so the button offers to pause.
        waitForContentDescription(string(R.string.media_viewer_pause))
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_pause)).performClick()

        waitForContentDescription(string(R.string.media_viewer_play))
        assertEquals(1, playback.pauseCount)
    }

    @Test
    fun seekBar_seeksWhereItIsReleased() {
        val playback = ScriptedPlayback()
        render(playback)
        onMain { playback.listener.onReady(durationMs = 10_000) }

        waitForContentDescription(string(R.string.media_viewer_seek))
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_seek))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4_000f) }

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) { playback.seeks.isNotEmpty() }
        assertEquals(listOf(4_000L), playback.seeks)
        waitForText(time(positionMs = 4_000, durationMs = 10_000))
    }

    @Test
    fun fullscreen_hidesTheTopBar_andBackLeavesIt() {
        val playback = ScriptedPlayback()
        render(playback, fileName = VIDEO_NAME)
        onMain {
            playback.listener.onVideoChanged(hasVideo = true)
            playback.listener.onReady(durationMs = 10_000)
        }

        waitForContentDescription(string(R.string.media_viewer_fullscreen_enter))
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_fullscreen_enter)).performClick()
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(VIDEO_NAME).fetchSemanticsNodes().isEmpty()
        }

        onMain { activity.onBackPressedDispatcher.onBackPressed() }

        waitForText(VIDEO_NAME)
        waitForContentDescription(string(R.string.media_viewer_fullscreen_enter))
    }

    @Test
    fun fullscreen_controlsHideWhilePlaying_andATapBringsThemBack() {
        val playback = ScriptedPlayback()
        render(playback, fileName = VIDEO_NAME)
        onMain {
            playback.listener.onVideoChanged(hasVideo = true)
            playback.listener.onReady(durationMs = 10_000)
        }
        waitForContentDescription(string(R.string.media_viewer_fullscreen_enter))
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_fullscreen_enter)).performClick()

        // The hide delay, then the fade-out.
        composeTestRule.mainClock.advanceTimeBy(CONTROLS_HIDE_DELAY_MS + 1_000)
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_seek)).assertDoesNotExist()

        composeTestRule.onRoot().performTouchInput { click(center) }

        waitForContentDescription(string(R.string.media_viewer_seek))
    }

    @Test
    fun fullscreen_pausedControlsStay() {
        val playback = ScriptedPlayback()
        render(playback, fileName = VIDEO_NAME)
        onMain {
            playback.listener.onVideoChanged(hasVideo = true)
            playback.listener.onReady(durationMs = 10_000)
        }
        waitForContentDescription(string(R.string.media_viewer_pause))
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_pause)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_fullscreen_enter)).performClick()

        composeTestRule.mainClock.advanceTimeBy(CONTROLS_HIDE_DELAY_MS + 1_000)

        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_seek)).assertIsDisplayed()
    }

    @Test
    fun leavingTheScreen_pausesPlayback() {
        val playback = ScriptedPlayback()
        render(playback)
        onMain { playback.listener.onReady(durationMs = 10_000) }
        waitForContentDescription(string(R.string.media_viewer_pause))

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) { playback.pauseCount == 1 }
    }

    /** The twin of [leavingTheScreen_pausesPlayback]: a rotation stops the Activity as well. */
    @Test
    fun recreatingTheActivity_keepsPlaying() {
        val playback = ScriptedPlayback()
        render(playback)
        onMain { playback.listener.onReady(durationMs = 10_000) }
        waitForContentDescription(string(R.string.media_viewer_pause))

        composeTestRule.activityRule.scenario.recreate()

        assertEquals(0, playback.pauseCount)
    }

    @Test
    fun unplayableFile_showsTheError() {
        val file = File(testDir, AUDIO_NAME).apply { writeText("not audio") }
        render(file) { ExoMediaPlayback(activity) }

        waitForText(string(R.string.media_viewer_load_error))
        composeTestRule.onNodeWithContentDescription(string(R.string.media_viewer_seek)).assertDoesNotExist()
    }

    // ==================== Helpers ====================

    private fun render(playback: ScriptedPlayback, fileName: String = AUDIO_NAME) {
        render(File(testDir, fileName)) { playback }
    }

    /** The player is built, and the view model drives it, on the main thread, as in the app. */
    private fun render(file: File, playback: () -> MediaPlayback) {
        lateinit var viewModel: MediaViewerViewModel
        composeTestRule.runOnUiThread {
            viewModel = MediaViewerViewModel(
                filePath = file.absolutePath,
                source = "test",
                application = activity.application,
                playback = playback()
            )
        }
        composeTestRule.setContent {
            FileExplorerTheme {
                MediaViewerScreen(viewModel = viewModel, onBackClick = {})
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        composeTestRule.runOnUiThread(block)
    }

    private fun time(positionMs: Long, durationMs: Long) = activity.getString(
        R.string.media_viewer_position,
        MediaTimeFormatter.format(positionMs, durationMs),
        MediaTimeFormatter.format(durationMs, durationMs)
    )

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    /**
     * A [MediaPlayback] that plays nothing: [play] and [pause] report back at once, as the real
     * player does, and everything else it would report is up to the test.
     */
    private class ScriptedPlayback : MediaPlayback {
        override val player: Player? = null
        override var currentPositionMs: Long = 0
            private set

        lateinit var listener: MediaPlayback.Listener
            private set

        /** Written on the main thread, read from the test thread while it polls. */
        @Volatile
        var pauseCount = 0
            private set
        val seeks: MutableList<Long> = CopyOnWriteArrayList()

        private var playWhenReady = false

        override fun setListener(listener: MediaPlayback.Listener) {
            this.listener = listener
        }

        override fun open(file: File) = Unit

        override fun play() = setPlayWhenReady(true)

        override fun pause() {
            pauseCount++
            setPlayWhenReady(false)
        }

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            currentPositionMs = positionMs
        }

        override fun reload() = Unit

        override fun release() = Unit

        private fun setPlayWhenReady(value: Boolean) {
            if (playWhenReady == value) return
            playWhenReady = value
            listener.onPlayingChanged(value)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val CONTROLS_HIDE_DELAY_MS = 3_000L
        const val AUDIO_NAME = "voice memo.mp3"
        const val VIDEO_NAME = "family video.mp4"
    }
}
