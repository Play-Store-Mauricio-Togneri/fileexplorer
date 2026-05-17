package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.ClipboardManager
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
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
    val showCreateFolderDialog: Boolean = false,
    val itemToRename: FileItem? = null,
    val itemsToDelete: List<FileItem> = emptyList(),
    val itemsToCompress: List<FileItem> = emptyList(),
    val compressProgress: CompressProgress? = null,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val uncompressProgress: UncompressProgress? = null,
    val deleteProgress: DeleteProgress? = null
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
    data class ShowDeletePartialSuccess(val deleted: Int, val failed: Int) : FolderUiEvent
    data class ShareFiles(val files: List<FileItem>) : FolderUiEvent
}

class FolderViewModel(
    private val context: Context,
    private val initialPath: String,
    private val initialTitle: String?,
    private val fileRepository: FileRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        FolderUiState(
            currentPath = initialPath,
            displayTitle = initialTitle,
            sortMode = SortManager.currentSortMode
        )
    )
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FolderUiEvent>()
    val events: SharedFlow<FolderUiEvent> = _events.asSharedFlow()

    val clipboard = ClipboardManager.clipboard

    private var hasLoadedOnce = false
    private var compressionJob: Job? = null
    private var deleteJob: Job? = null

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { _state.value.currentPath }
    )

    init {
        observeShowHiddenPreference()
        observeSortModePreference()
        observeUncompressHandler()
    }

    private fun observeShowHiddenPreference() {
        viewModelScope.launch {
            preferencesRepository.showHidden
                .collect { showHidden ->
                    val shouldReload = !hasLoadedOnce || _state.value.showHidden != showHidden
                    _state.update { it.copy(showHidden = showHidden) }
                    if (shouldReload) {
                        loadFiles()
                        hasLoadedOnce = true
                    }
                }
        }
    }

    private fun observeSortModePreference() {
        viewModelScope.launch {
            SortManager.sortMode.collect { sortMode ->
                if (_state.value.sortMode != sortMode) {
                    _state.update { it.copy(sortMode = sortMode) }
                    if (hasLoadedOnce) {
                        loadFiles()
                    }
                }
            }
        }
    }

    private fun observeUncompressHandler() {
        viewModelScope.launch {
            uncompressHandler.state.collect { uncompressState ->
                _state.update {
                    it.copy(
                        itemToUncompress = uncompressState.itemToUncompress,
                        uncompressEntryCount = uncompressState.entryCount,
                        uncompressProgress = uncompressState.progress
                    )
                }
            }
        }
        viewModelScope.launch {
            uncompressHandler.events.collect { event ->
                when (event) {
                    is UncompressEvent.ShowToast -> {
                        _events.emit(FolderUiEvent.ShowToastRes(event.messageResId))
                    }
                    is UncompressEvent.ExtractionComplete -> {
                        loadFiles()
                    }
                }
            }
        }
    }

    fun refresh() {
        loadFiles()
    }

    fun setSortMode(sortMode: SortMode) {
        SortManager.setSortMode(sortMode)
        viewModelScope.launch {
            preferencesRepository.setSortMode(sortMode)
        }
    }

    fun toggleHiddenFiles() {
        viewModelScope.launch {
            preferencesRepository.setShowHidden(!_state.value.showHidden)
        }
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
            FileAction.Rename -> {
                val selected = getSelectedFiles()
                if (selected.size == 1) {
                    showRenameDialog(selected.first())
                }
            }
            FileAction.Compress -> {
                val selected = getSelectedFiles()
                if (selected.isNotEmpty()) {
                    showCompressDialog(selected)
                }
            }
            FileAction.Share -> onShare()
            FileAction.Delete -> {
                val selected = getSelectedFiles()
                if (selected.isNotEmpty()) {
                    showDeleteConfirmDialog(selected)
                }
            }
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

    fun showRenameDialog(file: FileItem) {
        _state.update { it.copy(itemToRename = file) }
    }

    fun dismissRenameDialog() {
        _state.update { it.copy(itemToRename = null) }
    }

    fun onRename(newName: String) {
        val file = _state.value.itemToRename ?: return
        viewModelScope.launch {
            val success = fileRepository.rename(file, newName)
            dismissRenameDialog()
            clearSelection()
            if (success) {
                loadFiles()
            } else {
                _events.emit(FolderUiEvent.ShowToastRes(R.string.rename_error))
            }
        }
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

    fun showDeleteConfirmDialog(files: List<FileItem>) {
        _state.update { it.copy(itemsToDelete = files) }
    }

    fun dismissDeleteConfirmDialog() {
        _state.update { it.copy(itemsToDelete = emptyList()) }
    }

    fun onDeleteConfirmed() {
        val files = _state.value.itemsToDelete
        if (files.isEmpty()) return
        dismissDeleteConfirmDialog()
        clearSelection()
        deleteJob = viewModelScope.launch {
            val totalFiles = withContext(Dispatchers.IO) {
                files.sumOf { countFiles(java.io.File(it.path)) }
            }
            if (totalFiles < DELETE_PROGRESS_THRESHOLD) {
                val success = fileRepository.delete(files)
                if (!success) {
                    _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
                }
                loadFiles()
            } else {
                try {
                    fileRepository.deleteWithProgress(files)
                        .collect { progress ->
                            _state.update { it.copy(deleteProgress = progress) }
                            if (progress.isComplete) {
                                _state.update { it.copy(deleteProgress = null) }
                                handleDeleteResult(progress)
                                loadFiles()
                            }
                        }
                } catch (e: Exception) {
                    _state.update { it.copy(deleteProgress = null) }
                    if (e is kotlinx.coroutines.CancellationException) {
                        _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_cancelled))
                    } else {
                        ErrorReporter.error(e, "delete_files")
                        _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
                    }
                    loadFiles()
                }
            }
        }
    }

    private suspend fun handleDeleteResult(progress: DeleteProgress) {
        when {
            progress.failedFiles == 0 -> { }
            progress.deletedFiles == 0 -> {
                _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
            }
            else -> {
                _events.emit(
                    FolderUiEvent.ShowDeletePartialSuccess(
                        deleted = progress.deletedFiles,
                        failed = progress.failedFiles
                    )
                )
            }
        }
    }

    private fun countFiles(file: java.io.File): Int {
        return if (file.isDirectory) {
            file.listFiles()?.sumOf { countFiles(it) } ?: 0
        } else {
            1
        }
    }

    fun cancelDelete() {
        deleteJob?.cancel()
        deleteJob = null
        _state.update { it.copy(deleteProgress = null) }
    }

    fun showCompressDialog(files: List<FileItem>) {
        _state.update { it.copy(itemsToCompress = files) }
    }

    fun dismissCompressDialog() {
        _state.update { it.copy(itemsToCompress = emptyList()) }
    }

    fun onCompress(zipName: String) {
        val files = _state.value.itemsToCompress
        if (files.isEmpty()) return
        val targetDir = _state.value.currentPath
        dismissCompressDialog()
        clearSelection()
        compressionJob = viewModelScope.launch {
            try {
                fileRepository.compressFiles(files, targetDir, zipName)
                    .collect { progress ->
                        _state.update { it.copy(compressProgress = progress) }
                        if (progress.isComplete) {
                            _state.update { it.copy(compressProgress = null) }
                            loadFiles()
                        }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(compressProgress = null) }
                if (e !is kotlinx.coroutines.CancellationException) {
                    ErrorReporter.error(e, "compress_files", "zip")
                    _events.emit(FolderUiEvent.ShowToastRes(R.string.compress_error))
                }
            }
        }
    }

    fun cancelCompression() {
        compressionJob?.cancel()
        compressionJob = null
        _state.update { it.copy(compressProgress = null) }
    }

    fun showUncompressDialog(file: FileItem) {
        uncompressHandler.showUncompressDialog(file)
    }

    fun dismissUncompressDialog() {
        uncompressHandler.dismissUncompressDialog()
    }

    fun confirmUncompress() {
        uncompressHandler.confirmUncompress()
    }

    fun cancelUncompression() {
        uncompressHandler.cancelUncompression()
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
                ErrorReporter.critical(e, "load_files")
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
        private val context: Context,
        private val path: String,
        private val title: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val fileRepository = FileRepository()
            val preferencesRepository = PreferencesRepository(context.preferencesDataStore)
            return FolderViewModel(context.applicationContext, path, title, fileRepository, preferencesRepository) as T
        }
    }

    companion object {
        private const val DELETE_PROGRESS_THRESHOLD = 10
    }
}
