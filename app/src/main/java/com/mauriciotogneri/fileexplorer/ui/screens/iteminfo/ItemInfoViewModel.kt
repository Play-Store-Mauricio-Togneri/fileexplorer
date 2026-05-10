package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.util.AudioMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ImageMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class ItemInfoUiState(
    val isLoading: Boolean = true,
    val file: FileItem? = null,
    val imageMetadata: ImageMetadata? = null,
    val audioMetadata: AudioMetadata? = null,
    val error: Boolean = false
)

sealed interface ItemInfoUiEvent {
    data class OpenFile(val file: FileItem) : ItemInfoUiEvent
}

class ItemInfoViewModel(
    private val filePath: String
) : ViewModel() {

    private val _state = MutableStateFlow(ItemInfoUiState())
    val state: StateFlow<ItemInfoUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ItemInfoUiEvent>()
    val events: SharedFlow<ItemInfoUiEvent> = _events.asSharedFlow()

    init {
        loadFileInfo()
    }

    fun onOpenFile() {
        val file = _state.value.file ?: return
        if (file.isDirectory) return
        viewModelScope.launch {
            _events.emit(ItemInfoUiEvent.OpenFile(file))
        }
    }

    private fun loadFileInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = false) }
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val fileItem = FileItem.from(file)
                    val imageMetadata = if (fileItem.isImage) {
                        ImageMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    val audioMetadata = if (fileItem.isAudio) {
                        AudioMetadataExtractor.extract(file)
                    } else {
                        null
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            file = fileItem,
                            imageMetadata = imageMetadata,
                            audioMetadata = audioMetadata
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = true) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = true) }
            }
        }
    }

    class Factory(
        private val filePath: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ItemInfoViewModel(filePath) as T
        }
    }
}
