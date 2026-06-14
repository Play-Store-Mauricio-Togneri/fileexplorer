package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.app.Application
import android.content.Context
import android.os.StatFs
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.OperationProgress
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.DestinationNotWritableException
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileTransferIOException
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val isCurrentFolderRestricted: Boolean = false,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val showHidden: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val itemToRename: FileItem? = null,
    val itemsToDelete: List<FileItem> = emptyList(),
    val itemsToCompress: List<FileItem> = emptyList(),
    val compressProgress: CompressProgress? = null,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null,
    val deleteProgress: DeleteProgress? = null,
    val pickerRequest: PickerRequest? = null,
    val operationProgress: OperationProgress? = null,
    val pendingApkInstall: FileItem? = null
) {
    val isSelectionMode: Boolean get() = selectedPaths.isNotEmpty()
    val selectedCount: Int get() = selectedPaths.size
    val allSelected: Boolean get() = files.isNotEmpty() && selectedPaths.size == files.size
    val title: String get() = displayTitle ?: currentPath

    // selectedPaths is small; resolve it with a single pass over files (cached
    // per instance) instead of building a LinkedHashMap of every file just to
    // look up a few. selectedFiles is read during composition, e.g. by ActionBar.
    val selectedFiles: List<FileItem> by lazy {
        if (selectedPaths.isEmpty()) emptyList() else files.filter { it.path in selectedPaths }
    }

    val singleSelectedFile: FileItem?
        get() = if (selectedCount == 1) selectedFiles.firstOrNull() else null

    val allSelectedAreFiles: Boolean
        get() = selectedFiles.let { selected -> selected.isNotEmpty() && selected.all { !it.isDirectory } }

    val existingFileNames: Set<String>
        get() = files.mapTo(mutableSetOf()) { it.name }
}

/**
 * One-time UI events emitted by the ViewModel.
 */
sealed interface FolderUiEvent {
    data class ShowToast(val message: String) : FolderUiEvent
    data class ShowToastRes(@param:StringRes val messageResId: Int) : FolderUiEvent
    data class ShowDeletePartialSuccess(val deleted: Int, val failed: Int) : FolderUiEvent
    data class ShareFiles(val files: List<FileItem>) : FolderUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModel(
    application: Application,
    initialPath: String,
    initialTitle: String?,
    private val fileRepository: FileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val storageRepository: StorageRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val countDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_COUNTS)
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _state = MutableStateFlow(
        FolderUiState(
            currentPath = initialPath,
            displayTitle = initialTitle,
            sortMode = SortManager.sortMode.value
        )
    )
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private val _childCounts = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val childCounts: StateFlow<Map<String, Int?>> = _childCounts.asStateFlow()

    private var loadJob: Job? = null

    private val _events = MutableSharedFlow<FolderUiEvent>()
    val events: SharedFlow<FolderUiEvent> = _events.asSharedFlow()

    val showFolderContextMenuBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var hasLoadedOnce = false
    private var hasHandledInitialResume = false
    private var compressionJob: Job? = null
    private var deleteJob: Job? = null
    private var operationJob: Job? = null

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { _state.value.currentPath },
        getAllowedRoots = { storageRepository.getStorages().map { it.path } }
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
                        isPasswordProtected = uncompressState.isPasswordProtected,
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

    fun dismissFolderContextMenuBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU)
        }
    }

    fun refresh() {
        loadFiles()
    }

    /**
     * Called when the screen returns to RESUMED. Reloads so changes made while away are reflected
     * (e.g. a file copied into this folder from a child screen). The very first resume is skipped:
     * it coincides with the initial load already kicked off on creation, and reloading there would
     * needlessly cancel and restart it. The flag lives here, not in the composable, because the
     * ViewModel outlives the composition across child-folder navigation (the composable is disposed
     * when a child folder is pushed, which would otherwise reset a composable-held flag and suppress
     * the reload on the way back).
     */
    fun onScreenResumed() {
        if (hasHandledInitialResume) {
            loadFiles()
        } else {
            hasHandledInitialResume = true
        }
    }

    fun setSortMode(sortMode: SortMode) {
        SortManager.setSortMode(sortMode)
        viewModelScope.launch {
            preferencesRepository.setSortMode(sortMode)
        }
    }

    fun toggleHiddenFiles() {
        val newValue = !_state.value.showHidden
        AnalyticsTracker.setUserProperty("show_hidden_files", newValue.toString())
        viewModelScope.launch {
            preferencesRepository.setShowHidden(newValue)
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
            FileAction.MoveTo -> onMoveTo()
            FileAction.CopyTo -> onCopyTo()
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
            FileAction.Uncompress -> {
                val selected = getSelectedFiles()
                if (selected.size == 1 && selected.first().isZip) {
                    showUncompressDialog(selected.first())
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

    private fun onMoveTo() {
        val selectedItems = getSelectedFiles()
        if (selectedItems.isEmpty()) return

        _state.update {
            it.copy(
                pickerRequest = PickerRequest(
                    items = selectedItems,
                    mode = OperationMode.MOVE
                ),
                selectedPaths = emptySet()
            )
        }
    }

    private fun onCopyTo() {
        val selectedItems = getSelectedFiles()
        if (selectedItems.isEmpty()) return

        _state.update {
            it.copy(
                pickerRequest = PickerRequest(
                    items = selectedItems,
                    mode = OperationMode.COPY
                ),
                selectedPaths = emptySet()
            )
        }
    }

    fun dismissPicker() {
        _state.update { it.copy(pickerRequest = null) }
    }

    fun executeOperation(targetPath: String) {
        val request = _state.value.pickerRequest ?: return
        dismissPicker()

        operationJob = viewModelScope.launch {
            try {
                val (totalSize, availableBytes) = withContext(ioDispatcher) {
                    val size = fileRepository.totalSize(request.items)
                    val available = StatFs(targetPath).availableBytes
                    size to available
                }

                if (availableBytes < totalSize) {
                    _events.emit(FolderUiEvent.ShowToastRes(R.string.error_not_enough_space))
                    return@launch
                }

                executeOperationInternal(request.items, targetPath, request.mode)
            } finally {
                operationJob = null
            }
        }
    }

    private suspend fun executeOperationInternal(
        items: List<FileItem>,
        targetPath: String,
        mode: OperationMode
    ) {
        try {
            val allowedRoots = storageRepository.getStorages().map { it.path }

            fileRepository.copyFiles(
                sources = items,
                targetDir = targetPath,
                deleteAfter = (mode == OperationMode.MOVE),
                allowedRoots = allowedRoots
            ).collect { copyProgress ->
                _state.update {
                    it.copy(
                        operationProgress = OperationProgress(
                            mode = mode,
                            currentFile = copyProgress.currentFile,
                            copiedBytes = copyProgress.copiedBytes,
                            totalBytes = copyProgress.totalBytes,
                            isCancelling = it.operationProgress?.isCancelling ?: false
                        )
                    )
                }

                if (copyProgress.isComplete) {
                    MediaStoreUtil.scanFiles(context, copyProgress.createdPaths)

                    val actionName = if (mode == OperationMode.MOVE) "move" else "copy"
                    if (mode == OperationMode.MOVE && copyProgress.sourceDeleteFailed) {
                        // The copy succeeded but one or more originals could not be removed
                        // (e.g. a read-only source volume). Don't notify MediaStore that the
                        // sources are gone, and report the move as failed rather than a clean
                        // success — the originals are still on disk.
                        AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, false)
                        AnalyticsTracker.trackOperationFailed(actionName, "source_delete_failed")
                        _events.emit(FolderUiEvent.ShowToastRes(R.string.error_move_source_not_deleted))
                    } else {
                        if (mode == OperationMode.MOVE) {
                            MediaStoreUtil.notifyDeleted(context, copyProgress.deletedSourcePaths)
                        }
                        AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, true)
                    }
                    _state.update { it.copy(operationProgress = null) }
                    loadFiles()
                }
            }
        } catch (_: CancellationException) {
            _state.update { it.copy(operationProgress = null) }
            loadFiles()
        } catch (e: SecurityException) {
            val actionName = if (mode == OperationMode.MOVE) "move" else "copy"
            AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, false)
            AnalyticsTracker.trackOperationFailed(actionName, "invalid_target_path")
            ErrorReporter.error(e, "file_operation", "invalid_target_path")
            _state.update { it.copy(operationProgress = null) }
            _events.emit(FolderUiEvent.ShowToastRes(R.string.error_invalid_target_path))
            loadFiles()
        } catch (_: DestinationNotWritableException) {
            // The OS rejected the write operation (e.g. removable/scoped-storage volume that passes
            // canWrite() but denies the actual create). Environmental, not an app bug — show
            // the same failure toast but don't report it to Crashlytics.
            val actionName = if (mode == OperationMode.MOVE) "move" else "copy"
            AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, false)
            AnalyticsTracker.trackOperationFailed(actionName, "destination_not_writable")
            _state.update { it.copy(operationProgress = null) }
            val errorRes = if (mode == OperationMode.MOVE) {
                R.string.error_move_failed
            } else {
                R.string.error_copy_failed
            }
            _events.emit(FolderUiEvent.ShowToastRes(errorRes))
            loadFiles()
        } catch (_: FileTransferIOException) {
            // An I/O error during the byte transfer (e.g. EIO from removable storage unmounted
            // mid-copy). Environmental, not an app bug — show the failure toast but don't report
            // it to Crashlytics.
            val actionName = if (mode == OperationMode.MOVE) "move" else "copy"
            AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, false)
            AnalyticsTracker.trackOperationFailed(actionName, "storage_io_error")
            _state.update { it.copy(operationProgress = null) }
            val errorRes = if (mode == OperationMode.MOVE) {
                R.string.error_move_failed
            } else {
                R.string.error_copy_failed
            }
            _events.emit(FolderUiEvent.ShowToastRes(errorRes))
            loadFiles()
        } catch (e: Exception) {
            val actionName = if (mode == OperationMode.MOVE) "move" else "copy"
            AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, false)
            AnalyticsTracker.trackOperationFailed(actionName, "exception")
            ErrorReporter.error(e, "file_operation", actionName)
            _state.update { it.copy(operationProgress = null) }
            val errorRes = if (mode == OperationMode.MOVE) {
                R.string.error_move_failed
            } else {
                R.string.error_copy_failed
            }
            _events.emit(FolderUiEvent.ShowToastRes(errorRes))
            loadFiles()
        }
    }

    fun cancelOperation() {
        _state.update { currentState ->
            currentState.copy(
                operationProgress = currentState.operationProgress?.copy(isCancelling = true)
            )
        }
        operationJob?.cancel()
        operationJob = null
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
            val result = fileRepository.rename(file, newName)
            dismissRenameDialog()
            clearSelection()
            if (result != null) {
                if (!result.isCaseOnlyRename) {
                    MediaStoreUtil.notifyDeleted(context, listOf(result.oldPath))
                }
                MediaStoreUtil.scanFile(context, result.newPath)
                AnalyticsTracker.trackRenameCompleted(
                    FileExtensionUtil.getExtension(result.newPath),
                    file.mimeType
                )
                loadFiles()
            } else {
                AnalyticsTracker.trackOperationFailed("rename", "unknown")
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
        val itemCount = files.size
        dismissDeleteConfirmDialog()
        clearSelection()
        deleteJob = viewModelScope.launch {
            try {
                val allPaths = fileRepository.collectAllPaths(files)
                val totalFiles = allPaths.size
                if (totalFiles < DELETE_PROGRESS_THRESHOLD) {
                    val success = fileRepository.delete(files)
                    if (success) {
                        MediaStoreUtil.notifyDeleted(context, allPaths)
                        AnalyticsTracker.trackDeleteCompleted(itemCount, "folder")
                    } else {
                        AnalyticsTracker.trackOperationFailed("delete", "unknown")
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
                                    handleDeleteResult(progress, itemCount)
                                    // Mirror the small-delete branch: only tell MediaStore the files
                                    // are gone when every node was actually deleted. Notifying on a
                                    // partial failure would purge still-present files from MediaStore
                                    // views (they self-heal only on the next full media scan).
                                    if (progress.failedFiles == 0 && !progress.structuralDeleteFailed) {
                                        MediaStoreUtil.notifyDeleted(context, allPaths)
                                    }
                                    loadFiles()
                                }
                            }
                    } catch (e: Exception) {
                        _state.update { it.copy(deleteProgress = null) }
                        if (e is CancellationException) {
                            _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_cancelled))
                        } else {
                            AnalyticsTracker.trackOperationFailed("delete", "exception")
                            ErrorReporter.error(e, "delete_files")
                            _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
                        }
                        loadFiles()
                    }
                }
            } finally {
                deleteJob = null
            }
        }
    }

    private suspend fun handleDeleteResult(progress: DeleteProgress, itemCount: Int) {
        when {
            progress.failedFiles == 0 && !progress.structuralDeleteFailed -> {
                AnalyticsTracker.trackDeleteCompleted(itemCount, "folder")
            }
            progress.failedFiles > 0 && progress.deletedFiles == 0 -> {
                AnalyticsTracker.trackOperationFailed("delete", "all_failed")
                _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
            }
            progress.failedFiles > 0 -> {
                AnalyticsTracker.trackOperationFailed("delete", "partial")
                _events.emit(
                    FolderUiEvent.ShowDeletePartialSuccess(
                        deleted = progress.deletedFiles,
                        failed = progress.failedFiles
                    )
                )
            }
            else -> {
                // Every file was deleted, but a directory or symlink could not be removed
                // (e.g. a read-only parent). Mirror the small-delete path and report an error.
                AnalyticsTracker.trackOperationFailed("delete", "structural")
                _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
            }
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
        val itemCount = files.size
        val targetDir = _state.value.currentPath
        dismissCompressDialog()
        clearSelection()
        compressionJob = viewModelScope.launch {
            try {
                val allowedRoots = storageRepository.getStorages().map { it.path }
                fileRepository.compressFiles(files, targetDir, zipName, allowedRoots)
                    .collect { progress ->
                        _state.update { it.copy(compressProgress = progress) }
                        if (progress.isComplete) {
                            _state.update { it.copy(compressProgress = null) }
                            progress.outputPath?.let { MediaStoreUtil.scanFile(context, it) }
                            AnalyticsTracker.trackCompressCompleted(itemCount)
                            loadFiles()
                        }
                    }
            } catch (e: SecurityException) {
                _state.update { it.copy(compressProgress = null) }
                AnalyticsTracker.trackOperationFailed("compress", "invalid_target_path")
                ErrorReporter.error(e, "compress_files", "invalid_target_path")
                _events.emit(FolderUiEvent.ShowToastRes(R.string.error_invalid_target_path))
            } catch (e: Exception) {
                _state.update { it.copy(compressProgress = null) }
                if (e !is CancellationException) {
                    AnalyticsTracker.trackOperationFailed("compress", "exception")
                    ErrorReporter.error(e, "compress_files", "zip")
                    _events.emit(FolderUiEvent.ShowToastRes(R.string.compress_error))
                }
            } finally {
                compressionJob = null
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

    fun confirmUncompress(password: String? = null) {
        uncompressHandler.confirmUncompress(password)
    }

    fun cancelUncompression() {
        uncompressHandler.cancelUncompression()
    }

    fun setPendingApkInstall(file: FileItem?) {
        _state.update { it.copy(pendingApkInstall = file) }
    }

    fun clearPendingApkInstall() {
        _state.update { it.copy(pendingApkInstall = null) }
    }

    private fun loadFiles() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val currentState = _state.value
                val files = fileRepository.listFiles(
                    path = currentState.currentPath,
                    showHidden = currentState.showHidden,
                    sortMode = currentState.sortMode
                )
                val isRestricted = files.isEmpty() && withContext(countDispatcher) {
                    fileRepository.countChildren(currentState.currentPath) == null
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        files = files,
                        selectedPaths = emptySet(),
                        error = null,
                        isCurrentFolderRestricted = isRestricted
                    )
                }
                loadChildCounts(files)
            } catch (_: CancellationException) {
                // A newer load superseded this one (loadJob was cancelled). Leave the state for
                // that load to own, instead of flashing a spurious "unable to load" error.
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_load_files),
                        isCurrentFolderRestricted = false
                    )
                }
            }
        }
    }

    /**
     * Loads each directory's child count off the blocking list load. Jobs are submitted in
     * display order and bounded by [countDispatcher]; results overwrite the map in place, and
     * entries for paths no longer present are pruned. A null result (directory can't be read, e.g.
     * scoped-storage folders) is stored as a present-null entry, so the UI can distinguish "still
     * loading" (absent) from "restricted" (present-null). Runs as children of [loadJob], so a new
     * load cancels pending counts.
     */
    private fun CoroutineScope.loadChildCounts(files: List<FileItem>) {
        val directoryPaths = files.filter { it.isDirectory }.map { it.path }
        val retained = directoryPaths.toSet()
        _childCounts.update { current -> current.filterKeys { it in retained } }

        directoryPaths.forEach { path ->
            launch(countDispatcher) {
                val count = fileRepository.countChildren(path)
                _childCounts.update { it + (path to count) }
            }
        }
    }

    class Factory(
        private val application: Application,
        private val path: String,
        private val title: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val fileRepository = FileRepository()
            val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
            val storageRepository = StorageRepository(AndroidStorageSource(application))
            return FolderViewModel(
                application,
                path,
                title,
                fileRepository,
                preferencesRepository,
                storageRepository
            ) as T
        }
    }

    companion object {
        private const val MAX_CONCURRENT_COUNTS = 12
        private const val DELETE_PROGRESS_THRESHOLD = 10
    }
}
