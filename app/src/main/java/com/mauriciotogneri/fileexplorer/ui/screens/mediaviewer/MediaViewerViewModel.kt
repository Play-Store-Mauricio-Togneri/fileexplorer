package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.source.ExoMediaPlayback
import com.mauriciotogneri.fileexplorer.data.source.MediaPlayback
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import com.mauriciotogneri.fileexplorer.data.util.ThumbnailFileType
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the viewer's body shows. */
@Immutable
sealed interface MediaViewerContent {
    data object Loading : MediaViewerContent

    /** The file is playable; the controls apply. */
    data object Ready : MediaViewerContent

    /** Missing, corrupted, unsupported, or failed while playing. */
    data object LoadError : MediaViewerContent
}

@Immutable
data class MediaViewerUiState(
    val fileName: String = "",
    val content: MediaViewerContent = MediaViewerContent.Loading,
    /** Whether there are video frames to show; an audio file, or a video this device cannot decode, has none. */
    val hasVideo: Boolean = false,
    /** Whether playback is requested, which stays true while it buffers. */
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val fullscreen: Boolean = false
)

/**
 * The in-app fallback player for audio and video files. Playback starts on open and lasts only
 * while the screen is visible: [onStop] pauses it, and nothing resumes it but the user.
 */
class MediaViewerViewModel(
    private val filePath: String,
    private val source: String,
    application: Application,
    private val playback: MediaPlayback,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(MediaViewerUiState(fileName = File(filePath).name))
    val state: StateFlow<MediaViewerUiState> = _state.asStateFlow()

    /** For the video surface only. */
    val player: Player? get() = playback.player

    private var ended = false
    private var tracked = false
    private var progressJob: Job? = null

    private val listener = object : MediaPlayback.Listener {
        override fun onReady(durationMs: Long) {
            if (_state.value.content == MediaViewerContent.LoadError) return
            _state.update {
                it.copy(
                    content = MediaViewerContent.Ready,
                    durationMs = durationMs,
                    positionMs = playback.currentPositionMs
                )
            }
            if (!tracked) {
                tracked = true
                viewModelScope.launch { trackOpened() }
            }
        }

        override fun onPlayingChanged(playing: Boolean) {
            _state.update { it.copy(playing = playing, positionMs = playback.currentPositionMs) }
            progressJob?.cancel()
            if (playing) {
                progressJob = viewModelScope.launch { followProgress() }
            }
        }

        override fun onVideoChanged(hasVideo: Boolean) {
            _state.update { it.copy(hasVideo = hasVideo, fullscreen = it.fullscreen && hasVideo) }
        }

        override fun onEnded() {
            ended = true
            _state.update { it.copy(positionMs = it.durationMs) }
        }

        override fun onError(error: Throwable, expected: Boolean) {
            if (_state.value.content == MediaViewerContent.LoadError) return
            progressJob?.cancel()
            _state.update {
                it.copy(content = MediaViewerContent.LoadError, playing = false, fullscreen = false)
            }
            if (expected) {
                // Missing, corrupted or unsupported: expected, and already shown in the error UI.
                AnalyticsTracker.trackMediaViewerLoadError(source, REASON_UNPLAYABLE)
            } else {
                ErrorReporter.warning(error.scrubbed(), "media_viewer_play", fileType())
                AnalyticsTracker.trackMediaViewerLoadError(source, REASON_ERROR)
            }
        }
    }

    init {
        playback.setListener(listener)
        playback.open(File(filePath))
        playback.play()
    }

    private suspend fun followProgress() {
        while (true) {
            _state.update { it.copy(positionMs = playback.currentPositionMs) }
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    private suspend fun trackOpened() {
        val item = withContext(ioDispatcher) { FileItem.from(File(filePath)) }
        IntentUtil.trackRecentFile(context, item)
        AnalyticsTracker.trackFileOpened(
            FileExtensionUtil.getExtension(filePath),
            item.mimeType,
            source
        )
        AnalyticsTracker.trackMediaViewerOpened(source, if (_state.value.hasVideo) KIND_VIDEO else KIND_AUDIO)
    }

    private fun fileType(): String =
        if (MimeTypeUtil.isVideo(MimeTypeUtil.getMimeType(File(filePath)))) {
            ThumbnailFileType.VIDEO
        } else {
            ThumbnailFileType.AUDIO
        }

    // ==================== Controls ====================

    fun togglePlay() {
        if (_state.value.content != MediaViewerContent.Ready) return
        if (_state.value.playing) {
            playback.pause()
        } else {
            if (ended) {
                // Play at the end starts over.
                seekTo(0)
            }
            playback.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (_state.value.content != MediaViewerContent.Ready) return
        val target = positionMs.coerceIn(0, _state.value.durationMs)
        ended = false
        playback.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun toggleFullscreen() {
        _state.update { it.copy(fullscreen = !it.fullscreen && it.hasVideo) }
    }

    fun exitFullscreen() {
        _state.update { it.copy(fullscreen = false) }
    }

    // ==================== Lifecycle ====================

    /** The screen is no longer visible: pause, and leave resuming to the user. */
    fun onStop() {
        playback.pause()
    }

    override fun onCleared() {
        progressJob?.cancel()
        playback.release()
    }

    class Factory(
        private val filePath: String,
        private val source: String,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MediaViewerViewModel(
                filePath = filePath,
                source = source,
                application = application,
                playback = ExoMediaPlayback(application)
            ) as T
        }
    }

    companion object {
        /** How often the position follows playback: often enough for a seconds display and a slider. */
        internal const val PROGRESS_INTERVAL_MS = 250L

        private const val REASON_UNPLAYABLE = "unplayable"
        private const val REASON_ERROR = "error"
        private const val KIND_AUDIO = "audio"
        private const val KIND_VIDEO = "video"
    }
}
