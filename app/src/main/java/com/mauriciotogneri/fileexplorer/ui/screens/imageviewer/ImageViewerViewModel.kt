package com.mauriciotogneri.fileexplorer.ui.screens.imageviewer

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
data class ImageViewerUiState(
    val filePath: String = "",
    val fileName: String = "",
    val file: FileItem? = null
)

sealed interface ImageViewerUiEvent {
    data object Finish : ImageViewerUiEvent
    data class ShowToast(val messageResId: Int) : ImageViewerUiEvent
}

class ImageViewerViewModel(
    private val filePath: String,
    private val source: String,
    application: Application,
    private val fileRepository: FileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _state =
        MutableStateFlow(ImageViewerUiState(filePath = filePath, fileName = File(filePath).name))
    val state: StateFlow<ImageViewerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ImageViewerUiEvent>()
    val events: SharedFlow<ImageViewerUiEvent> = _events.asSharedFlow()

    // Coil renders the image and drives the success/error slots, re-invoking them across
    // recompositions; guard so the recent-file + analytics tracking fires at most once per viewing.
    private var loadTracked = false

    init {
        loadFile()
    }

    private fun loadFile() {
        viewModelScope.launch {
            val fileItem = withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            _state.update { it.copy(file = fileItem) }
        }
    }

    fun onImageLoaded() {
        if (loadTracked) return
        loadTracked = true
        viewModelScope.launch {
            val item = _state.value.file ?: withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            IntentUtil.trackRecentFile(context, item)
            AnalyticsTracker.trackFileOpened(
                FileExtensionUtil.getExtension(filePath),
                item.mimeType,
                source
            )
            AnalyticsTracker.trackImageViewerOpened(source)
        }
    }

    fun onImageLoadError(throwable: Throwable?) {
        if (loadTracked) return
        loadTracked = true
        throwable?.let { ErrorReporter.warning(it, "image_viewer_load") }
        AnalyticsTracker.trackImageViewerLoadError(source)
    }

    fun onShareClicked() {
        AnalyticsTracker.trackImageViewerShare(source)
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch {
            val item = _state.value.file ?: withContext(ioDispatcher) { FileItem.from(File(filePath)) }
            val success = fileRepository.delete(listOf(item))
            if (success) {
                MediaStoreUtil.notifyDeleted(context, listOf(filePath))
                _events.emit(ImageViewerUiEvent.Finish)
            } else {
                _events.emit(ImageViewerUiEvent.ShowToast(R.string.delete_error))
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
            return ImageViewerViewModel(
                filePath = filePath,
                source = source,
                application = application,
                fileRepository = FileRepository()
            ) as T
        }
    }
}
