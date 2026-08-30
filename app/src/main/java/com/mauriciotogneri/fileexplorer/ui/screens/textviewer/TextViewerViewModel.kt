package com.mauriciotogneri.fileexplorer.ui.screens.textviewer

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.util.TextFilePreview
import com.mauriciotogneri.fileexplorer.data.util.isUnreadableFile
import com.mauriciotogneri.fileexplorer.data.util.deleteFailureFor
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class TextViewerUiState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val lines: List<String> = emptyList(),
    val truncated: Boolean = false,
    val error: Boolean = false,
    val file: FileItem? = null
)

sealed interface TextViewerUiEvent {
    data object Finish : TextViewerUiEvent
    data class ShowToast(val messageResId: Int) : TextViewerUiEvent
}

class TextViewerViewModel(
    private val filePath: String,
    private val source: String,
    application: Application,
    private val fileRepository: FileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(TextViewerUiState(fileName = File(filePath).name))
    val state: StateFlow<TextViewerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TextViewerUiEvent>()
    val events: SharedFlow<TextViewerUiEvent> = _events.asSharedFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = false) }
            try {
                val file = File(filePath)
                val (preview, fileItem) = withContext(ioDispatcher) {
                    TextFilePreview.read(file, MAX_BYTES) to FileItem.from(file)
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        lines = preview.lines,
                        truncated = preview.truncated,
                        file = fileItem
                    )
                }
                // The line list is the largest structure this screen holds. Recorded so an
                // OutOfMemoryError report — whose stack names an unrelated allocation — shows how
                // much text was loaded behind it.
                ErrorReporter.setCount(KEY_TEXT_LINES, preview.lines.size)
                ErrorReporter.recordHeap()
                trackOpened(fileItem, preview.truncated)
            } catch (e: Exception) {
                // A file the viewer cannot open or read at all — deleted, renamed, or on a volume
                // unmounted since whoever launched the viewer listed it — is an expected condition
                // already shown in the error UI, not a bug (see isUnreadableFile). The analytics
                // counter below fires either way, capturing the overall failure rate.
                if (!isUnreadableFile(e)) {
                    ErrorReporter.warning(e.scrubbed(), "text_viewer_read")
                }
                AnalyticsTracker.trackTextViewerReadError(source)
                _state.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    private fun trackOpened(fileItem: FileItem, truncated: Boolean) {
        IntentUtil.trackRecentFile(context, fileItem)
        AnalyticsTracker.trackFileOpened(
            FileExtensionUtil.getExtension(filePath),
            fileItem.mimeType,
            source
        )
        AnalyticsTracker.trackTextViewerOpened(source)
        if (truncated) {
            AnalyticsTracker.trackTextViewerTruncated(source)
        }
    }

    fun onShareClicked() {
        AnalyticsTracker.trackTextViewerShare(source)
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch {
            // Stat'd here rather than read from state.file, which holds the stat taken when the
            // screen opened and is never refreshed. FileRepository.delete re-resolves the path
            // and decides recursion from a live stat of its own, so a snapshot from earlier in
            // the session cannot speak for it — and a path that was a file at open can be a
            // directory by the time the user confirms. Only path is read downstream, so this
            // resolves to the same item the cached one would, one moment later.
            val item = withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            // Deleting a directory would walk the whole tree behind a confirm dialog that named
            // a single file, and notifyDeleted below is the file-only variant, so every
            // descendant's MediaStore row would outlive it. This screen only ever identifies one
            // file: refuse anything else.
            if (item.isDirectory) {
                _events.emit(TextViewerUiEvent.ShowToast(R.string.delete_error))
                return@launch
            }
            val result = fileRepository.delete(listOf(item))
            if (result.success) {
                MediaStoreUtil.notifyDeleted(context, listOf(filePath))
                _events.emit(TextViewerUiEvent.Finish)
            } else {
                // Named rather than the generic message, the same as every other delete. No
                // analytics event: this screen has never reported one, and adding a source now
                // would move the operation_failed volume for a reason unrelated to the app.
                _events.emit(TextViewerUiEvent.ShowToast(deleteFailureFor(result.failureErrno).messageResId))
            }
        }
    }

    class Factory(
        private val filePath: String,
        private val source: String,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Drops the cached home-screen location sizes whenever this screen changes a file, so a
            // card is not left reporting a stale total until the cache TTL lapses.
            val locationsCacheSource = DataStoreLocationsCacheSource(application.locationsCacheDataStore)
            return TextViewerViewModel(
                filePath = filePath,
                source = source,
                application = application,
                fileRepository = FileRepository { locationsCacheSource.clearCache() }
            ) as T
        }
    }

    companion object {
        // Cap how much of a file we read/render: a single selectable buffer larger than this
        // risks jank. ~1 MB is still tens of thousands of lines of text.
        const val MAX_BYTES = 1024 * 1024
        private const val KEY_TEXT_LINES = "text_lines"
    }
}
