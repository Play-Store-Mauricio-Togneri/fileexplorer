package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class FolderUiState(
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val showHidden: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val selectedCount: Int get() = selectedPaths.size
    val allSelected: Boolean get() = files.isNotEmpty() && selectedPaths.size == files.size
}

class FolderViewModel(
    private val initialPath: String,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FolderUiState(currentPath = initialPath))
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    init {
        loadFiles()
    }

    fun refresh() {
        loadFiles()
    }

    fun setSortMode(sortMode: SortMode) {
        _state.update { it.copy(sortMode = sortMode) }
        loadFiles()
    }

    fun toggleHiddenFiles() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        loadFiles()
    }

    fun toggleSelection(file: FileItem) {
        _state.update { state ->
            val newSelected = if (file.path in state.selectedPaths) {
                state.selectedPaths - file.path
            } else {
                state.selectedPaths + file.path
            }
            state.copy(selectedPaths = newSelected)
        }
    }

    fun selectAll() {
        _state.update { state ->
            state.copy(selectedPaths = state.files.map { it.path }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPaths = emptySet()) }
    }

    fun getSelectedFiles(): List<FileItem> {
        val state = _state.value
        return state.files.filter { it.path in state.selectedPaths }
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val currentState = _state.value
                val files = fileRepository.listFiles(
                    path = currentState.currentPath,
                    showHidden = currentState.showHidden,
                    sortMode = currentState.sortMode
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        files = files,
                        selectedPaths = emptySet(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    class Factory(
        private val path: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = FileRepository()
            return FolderViewModel(path, repository) as T
        }
    }
}
