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
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.util.TextFilePreview
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
    val content: String = "",
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
                        content = preview.text,
                        lines = preview.lines,
                        truncated = preview.truncated,
                        file = fileItem
                    )
                }
                trackOpened(fileItem)
            } catch (e: Exception) {
                ErrorReporter.warning(e, "text_viewer_read")
                _state.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    private fun trackOpened(fileItem: FileItem) {
        IntentUtil.trackRecentFile(context, fileItem)
        AnalyticsTracker.trackFileOpened(
            FileExtensionUtil.getExtension(filePath),
            fileItem.mimeType,
            source
        )
        AnalyticsTracker.trackTextViewerOpened(source)
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch {
            val item = _state.value.file ?: withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            val success = fileRepository.delete(listOf(item))
            if (success) {
                MediaStoreUtil.notifyDeleted(context, listOf(filePath))
                _events.emit(TextViewerUiEvent.Finish)
            } else {
                _events.emit(TextViewerUiEvent.ShowToast(R.string.delete_error))
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
            return TextViewerViewModel(
                filePath = filePath,
                source = source,
                application = application,
                fileRepository = FileRepository()
            ) as T
        }
    }

    companion object {
        // Cap how much of a file we read/render: a single selectable buffer larger than this
        // risks jank. ~1 MB is still tens of thousands of lines of text.
        const val MAX_BYTES = 1024 * 1024
    }
}
