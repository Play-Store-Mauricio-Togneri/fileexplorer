package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.mauriciotogneri.fileexplorer.data.util.isUnplayableMedia
import java.io.File

/**
 * [MediaPlayback] on Media3's ExoPlayer. It holds audio focus while playing, so a call or another
 * app's audio pauses it, and pauses when headphones are unplugged. Must be created on the main
 * thread, which every later call has to come from as well.
 */
class ExoMediaPlayback(context: Context) : MediaPlayback {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var listener: MediaPlayback.Listener? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> listener?.onReady(exoPlayer.duration.coerceAtLeast(0))
                Player.STATE_ENDED -> {
                    // Left requesting playback, the player would restart on the next seek.
                    exoPlayer.pause()
                    listener?.onEnded()
                }
                Player.STATE_IDLE, Player.STATE_BUFFERING -> Unit
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            listener?.onPlayingChanged(playWhenReady)
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (tracks.isEmpty) return
            // A track beyond the decoder's advertised limits still counts: the track selector tries
            // it and it often plays, while one that really fails reports a decoder error.
            if (!tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities = */ true) &&
                !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities = */ true)
            ) {
                // Without a single decodable track the player would run silently to the end.
                exoPlayer.stop()
                listener?.onError(NoPlayableTrackException(), expected = true)
                return
            }
            listener?.onVideoChanged(tracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED) {
                // Media3 does not retry it: the player is idle, with the file and position kept.
                listener?.onReclaimed(error)
            } else {
                listener?.onError(error, expected = isUnplayableMedia(error.errorCode))
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    override val player: Player get() = exoPlayer

    // A time the player does not know yet is C.TIME_UNSET, which is negative.
    override val currentPositionMs: Long get() = exoPlayer.currentPosition.coerceAtLeast(0)

    override fun setListener(listener: MediaPlayback.Listener) {
        this.listener = listener
    }

    override fun open(file: File) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun reload() {
        exoPlayer.prepare()
    }

    override fun release() {
        listener = null
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }

    /** Every track of the file uses a format this device cannot decode. */
    private class NoPlayableTrackException : Exception("no playable track")
}
