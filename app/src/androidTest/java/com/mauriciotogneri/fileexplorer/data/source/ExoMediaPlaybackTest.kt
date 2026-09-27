package com.mauriciotogneri.fileexplorer.data.source

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

// device-required: ExoPlayer decodes through the platform MediaCodec and runs on a Looper.
/**
 * [ExoMediaPlayback] on the device's decoders, over the `sample_audio.mp3` (0.37 s, with a cover
 * image) and `sample_video.mp4` (0.2 s, H.264) assets: what it reports to its listener, which is
 * all the viewer sees of the player.
 */
@RunWith(AndroidJUnit4::class)
class ExoMediaPlaybackTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var testDir: File
    private lateinit var playback: ExoMediaPlayback
    private val events = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        testDir = File(instrumentation.targetContext.cacheDir, "media_playback_${System.nanoTime()}").apply { mkdirs() }
        // The player is built, called and released on the main thread, as in the app.
        onMain {
            playback = ExoMediaPlayback(instrumentation.targetContext)
            playback.setListener(RecordingListener(events))
        }
    }

    @After
    fun tearDown() {
        onMain { playback.release() }
        testDir.deleteRecursively()
    }

    @Test
    fun audio_playsToTheEnd_andStaysPausedThere() {
        open(asset("sample_audio.mp3"))
        onMain { playback.play() }

        awaitEvent(ENDED)

        assertTrue(events.contains("$VIDEO false"))
        awaitEvent("$PLAYING false")
        assertFalse(onMain { playback.player.playWhenReady })

        // A seek after the end must not start playing again on its own.
        onMain { playback.seekTo(0) }
        awaitEvent(READY, occurrence = 2)
        assertFalse(onMain { playback.player.playWhenReady })
    }

    @Test
    fun audio_reportsItsLength() {
        open(asset("sample_audio.mp3"))

        awaitEvent(READY)

        // The asset's header gives its length, so it is known by the time it is ready.
        assertTrue(events.any { it.startsWith(DURATION) && it != "$DURATION null" })
        assertFalse(events.contains("$READY null"))
    }

    @Test
    fun audio_reportsItsTitleAndArtist() {
        open(asset("sample_audio.mp3"))

        awaitEvent("$METADATA Fixture Track")

        assertTrue(events.contains("$METADATA Fixture Track|Fixture Artist"))
    }

    @Test
    fun muting_silencesTheSound_andKeepsPlaying() {
        open(asset("sample_audio.mp3"))
        onMain { playback.play() }

        onMain { playback.setMuted(true) }
        assertEquals(0f, onMain { playback.player.volume }, 0f)
        assertTrue(onMain { playback.player.playWhenReady })

        onMain { playback.setMuted(false) }
        assertEquals(1f, onMain { playback.player.volume }, 0f)
    }

    @Test
    fun video_reportsItsVideoTrack() {
        open(asset("sample_video.mp4"))

        awaitEvent(READY)

        assertTrue(events.contains("$VIDEO true"))
    }

    @Test
    fun malformedFile_isAnExpectedError() {
        open(File(testDir, "broken.mp3").apply { writeText("not audio") })

        awaitEvent(ERROR)

        assertEquals("$ERROR true", events.first { it.startsWith(ERROR) })
    }

    private fun open(file: File) {
        onMain { playback.open(file) }
    }

    private fun asset(name: String): File =
        DocumentFixtures.copyAsset(instrumentation.context, name, testDir)

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun awaitEvent(prefix: String, occurrence: Int = 1) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (events.count { it.startsWith(prefix) } < occurrence) {
            check(System.currentTimeMillis() < deadline) { "no '$prefix' #$occurrence within ${TIMEOUT_MS}ms, got $events" }
            Thread.sleep(POLL_MS)
        }
    }

    private class RecordingListener(private val events: MutableList<String>) : MediaPlayback.Listener {
        override fun onReady(durationMs: Long?) {
            events += "$READY $durationMs"
        }

        override fun onDurationChanged(durationMs: Long?) {
            events += "$DURATION $durationMs"
        }

        override fun onPlayingChanged(playing: Boolean) {
            events += "$PLAYING $playing"
        }

        override fun onVideoChanged(hasVideo: Boolean) {
            events += "$VIDEO $hasVideo"
        }

        override fun onMetadataChanged(title: String?, artist: String?) {
            events += "$METADATA $title|$artist"
        }

        override fun onEnded() {
            events += ENDED
        }

        override fun onReclaimed(error: Throwable) {
            events += RECLAIMED
        }

        override fun onError(error: Throwable, expected: Boolean) {
            events += "$ERROR $expected"
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
        const val READY = "ready"
        const val DURATION = "duration"
        const val PLAYING = "playing"
        const val VIDEO = "video"
        const val ENDED = "ended"
        const val METADATA = "metadata"
        const val RECLAIMED = "reclaimed"
        const val ERROR = "error"
    }
}
