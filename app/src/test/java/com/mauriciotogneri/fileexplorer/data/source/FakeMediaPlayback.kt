package com.mauriciotogneri.fileexplorer.data.source

import androidx.media3.common.Player
import java.io.File

/**
 * A [MediaPlayback] that plays nothing. Like the real player, [play] and [pause] report the change
 * to the listener at once; everything else the player would report — readiness, tracks, the end,
 * failures — is up to the test, through [listener].
 */
class FakeMediaPlayback(var positionMs: Long = 0) : MediaPlayback {

    override val player: Player? = null

    override val currentPositionMs: Long get() = positionMs

    lateinit var listener: MediaPlayback.Listener
        private set

    val openedFiles = mutableListOf<File>()
    val seeks = mutableListOf<Long>()
    var playCount = 0
        private set
    var pauseCount = 0
        private set
    var releaseCount = 0
        private set

    /** Calls that arrived after [release], which the real player would reject. */
    var callsAfterRelease = 0
        private set

    private var playWhenReady = false

    override fun setListener(listener: MediaPlayback.Listener) {
        this.listener = listener
    }

    override fun open(file: File) {
        checkNotReleased()
        openedFiles += file
    }

    override fun play() {
        checkNotReleased()
        playCount++
        setPlayWhenReady(true)
    }

    override fun pause() {
        checkNotReleased()
        pauseCount++
        setPlayWhenReady(false)
    }

    override fun seekTo(positionMs: Long) {
        checkNotReleased()
        seeks += positionMs
        this.positionMs = positionMs
    }

    override fun release() {
        checkNotReleased()
        releaseCount++
    }

    private fun setPlayWhenReady(value: Boolean) {
        if (playWhenReady == value) return
        playWhenReady = value
        listener.onPlayingChanged(value)
    }

    private fun checkNotReleased() {
        if (releaseCount > 0) callsAfterRelease++
    }
}
