package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.ClipboardManager
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class FolderUiState(
    val currentPath: String = "",
    val displayTitle: String? = null,
    val files: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val showHidden: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val infoDialogFile: FileItem? = null
) {
    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val selectedCount: Int get() = selectedPaths.size
    val allSelected: Boolean get() = files.isNotEmpty() && selectedPaths.size == files.size
    val title: String get() = displayTitle ?: currentPath
}

/**
 * One-time UI events emitted by the ViewModel.
 */
sealed interface FolderUiEvent {
    data class ShowToast(val message: String) : FolderUiEvent
    data class ShowToastRes(@StringRes val messageResId: Int) : FolderUiEvent
    data class ShareFiles(val files: List<FileItem>) : FolderUiEvent
}

class FolderViewModel(
    private val initialPath: String,
    private val initialTitle: String?,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FolderUiState(currentPath = initialPath, displayTitle = initialTitle))
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FolderUiEvent>()
    val events: SharedFlow<FolderUiEvent> = _events.asSharedFlow()

    val clipboard = ClipboardManager.clipboard

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

    fun onAction(action: FileAction) {
        when (action) {
            FileAction.Cut -> onCut()
            FileAction.Copy -> onCopy()
            FileAction.Paste -> onPaste()
            FileAction.SelectAll -> selectAll()
            FileAction.Rename -> showRenameDialog()
            FileAction.Share -> onShare()
            FileAction.Delete -> showDeleteConfirmDialog()
            FileAction.CreateFolder -> showCreateFolderDialog()
        }
    }

    private fun onCut() {
        val selectedFiles = getSelectedFiles()
        if (selectedFiles.isNotEmpty()) {
            ClipboardManager.cut(selectedFiles, _state.value.currentPath)
            clearSelection()
        }
    }

    private fun onCopy() {
        val selectedFiles = getSelectedFiles()
        if (selectedFiles.isNotEmpty()) {
            ClipboardManager.copy(selectedFiles, _state.value.currentPath)
            clearSelection()
        }
    }

    private fun onPaste() {
        // TODO: Implement in Phase 7
        // Will copy/move files from clipboard to current directory
    }

    private fun onShare() {
        val selectedFiles = getSelectedFiles().filter { !it.isDirectory }
        if (selectedFiles.isNotEmpty()) {
            viewModelScope.launch {
                _events.emit(FolderUiEvent.ShareFiles(selectedFiles))
            }
            clearSelection()
        }
    }

    fun showRenameDialog() {
        _state.update { it.copy(showRenameDialog = true) }
    }

    fun dismissRenameDialog() {
        _state.update { it.copy(showRenameDialog = false) }
    }

    fun onRename(newName: String) {
        // TODO: Implement in Phase 7
        dismissRenameDialog()
        clearSelection()
    }

    fun showCreateFolderDialog() {
        _state.update { it.copy(showCreateFolderDialog = true) }
    }

    fun dismissCreateFolderDialog() {
        _state.update { it.copy(showCreateFolderDialog = false) }
    }

    fun onCreateFolder(name: String) {
        if (name.isBlank()) {
            dismissCreateFolderDialog()
            return
        }

        viewModelScope.launch {
            val success = fileRepository.createFolder(_state.value.currentPath, name)
            dismissCreateFolderDialog()
            if (success) {
                loadFiles()
            } else {
                _events.emit(FolderUiEvent.ShowToastRes(R.string.create_error))
            }
        }
    }

    fun showDeleteConfirmDialog() {
        _state.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _state.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun onDeleteConfirmed() {
        // TODO: Implement in Phase 7
        dismissDeleteConfirmDialog()
        clearSelection()
    }

    fun showInfoDialog(file: FileItem) {
        _state.update { it.copy(infoDialogFile = file) }
    }

    fun dismissInfoDialog() {
        _state.update { it.copy(infoDialogFile = null) }
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
        private val path: String,
        private val title: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = FileRepository()
            return FolderViewModel(path, title, repository) as T
        }
    }
}
