package com.mauriciotogneri.fileexplorer.data.source

import androidx.media3.common.Player
import java.io.File

/**
 * One audio or video file played by the in-app viewer. Every call, and every [Listener] callback,
 * happens on the main thread.
 *
 * Kept narrower than Media3's [Player] so the viewer's ViewModel never handles a Media3 type:
 * `PlaybackException` and `Tracks` cannot be built in a JVM unit test.
 */
interface MediaPlayback {

    /** The player to draw video frames from, or null when there is nothing to draw them with. */
    val player: Player?

    /** Where playback is, in milliseconds. */
    val currentPositionMs: Long

    fun setListener(listener: Listener)

    /** Starts loading [file]. Nothing plays until [play]. */
    fun open(file: File)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /**
     * Loads the opened file again where playback stopped, after [Listener.onReclaimed]. Keeps
     * whether playback is requested.
     */
    fun reload()

    /** Frees the decoders. Nothing may be called afterwards. */
    fun release()

    interface Listener {

        /** The file is ready to play; called again after every seek that had to buffer. */
        fun onReady(durationMs: Long)

        /** Whether playback is requested, which stays true while it buffers. */
        fun onPlayingChanged(playing: Boolean)

        /** Whether the file has a video track this device can show. */
        fun onVideoChanged(hasVideo: Boolean)

        /** Playback reached the end and paused there. */
        fun onEnded()

        /**
         * The system took the decoders for another app. The file is fine: playback stopped where it
         * was, and [reload] picks it up again. [error] is worth reporting only if it keeps happening.
         */
        fun onReclaimed(error: Throwable)

        /**
         * The file cannot be played. [expected] for a missing, corrupted or unsupported file, which
         * is not a bug; otherwise [error] is worth reporting.
         */
        fun onError(error: Throwable, expected: Boolean)
    }
}
