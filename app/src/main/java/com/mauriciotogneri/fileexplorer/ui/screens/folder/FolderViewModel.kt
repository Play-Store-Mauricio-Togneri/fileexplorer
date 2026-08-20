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
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.OperationProgress
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.DestinationNotWritableException
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileTransferIOException
import com.mauriciotogneri.fileexplorer.data.repository.InsufficientStorageException
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@Immutable
data class FolderUiState(
    val currentPath: String = "",
    val displayTitle: String? = null,
    val files: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCurrentFolderRestricted: Boolean = false,
    val isStorageRoot: Boolean? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val showHidden: Boolean = false,
    val folderSecondLine: FolderSecondLine = FolderSecondLine.ITEM_COUNT,
    val fileSecondLine: FileSecondLine = FileSecondLine.SIZE,
    val swipeLeftAction: SwipeAction = SwipeAction.RENAME,
    val swipeRightAction: SwipeAction = SwipeAction.DELETE,
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
    val pendingApkInstall: FileItem? = null,
    val favoritePaths: Set<String> = emptySet()
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
    // Exposed so the destination picker this screen hosts operates through the same repository
    // rather than building its own: only the one constructed by [Factory] carries the hook that
    // drops the home screen's cached location sizes, and a composable is not a place to open a
    // DataStore.
    val fileRepository: FileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val storageRepository: StorageRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentFilesRepository: RecentFilesRepository,
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
        observeSecondLinePreferences()
        observeSwipeActionPreferences()
        observeSortModePreference()
        observeUncompressHandler()
        observeFavorites()
        determineStorageRoot()
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

    /**
     * Both second-line settings only choose what an already-loaded row displays, so a change
     * updates the state without reloading the folder or recounting anything.
     */
    private fun observeSecondLinePreferences() {
        viewModelScope.launch {
            preferencesRepository.folderSecondLine.collect { secondLine ->
                _state.update { it.copy(folderSecondLine = secondLine) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.fileSecondLine.collect { secondLine ->
                _state.update { it.copy(fileSecondLine = secondLine) }
            }
        }
    }

    /**
     * Both swipe settings only choose what a row's gesture reveals, so a change updates the state
     * without reloading the folder.
     */
    private fun observeSwipeActionPreferences() {
        viewModelScope.launch {
            preferencesRepository.swipeLeftAction.collect { action ->
                _state.update { it.copy(swipeLeftAction = action) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.swipeRightAction.collect { action ->
                _state.update { it.copy(swipeRightAction = action) }
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

    // Exposes only the set of favorited paths; rows look up membership to show the star and the
    // bottom sheet derives its Add/Remove label. The existence filter in the repository runs
    // upstream of flowOn so it stays off the main thread.
    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.favoritesFlow
                .map { favorites -> favorites.mapTo(mutableSetOf()) { it.path } }
                .flowOn(ioDispatcher)
                .collect { paths -> _state.update { it.copy(favoritePaths = paths) } }
        }
    }

    // A storage root (its path equals a StorageDevice path) cannot be added to favorites, so resolve
    // once whether the folder being viewed is one. currentPath is fixed per VM instance — navigating
    // into a child creates a new VM — so this never needs to re-run. isStorageRoot stays null until
    // resolved, keeping the favorite action hidden (rather than shown-then-hidden) while the async
    // lookup runs. getStorages() can throw on a storage unmount race; treat that as "not a root" so a
    // normal folder still offers the action.
    private fun determineStorageRoot() {
        viewModelScope.launch {
            val isRoot = try {
                storageRepository.getStorages().any { it.path == _state.value.currentPath }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update { it.copy(isStorageRoot = isRoot) }
        }
    }

    fun addToFavorites(file: FileItem) {
        viewModelScope.launch {
            favoritesRepository.addFavorite(file.path, file.name, file.isDirectory, file.mimeType)
        }
    }

    fun removeFromFavorites(file: FileItem) {
        viewModelScope.launch {
            favoritesRepository.removeFavorite(file.path)
        }
    }

    // Favorites the folder currently being viewed (the overflow-menu action), as opposed to a list
    // item. Stored the way a directory list item would be — isDirectory=true, empty mimeType, on-disk
    // name — so the entry is identical whether the folder is favorited from here or from its parent's
    // list (addFavorite dedupes by path). Storage roots are excluded upstream (the action is hidden
    // when isStorageRoot), so File(path).name is always a real folder name here, never a bare volume
    // segment like "0".
    fun addCurrentFolderToFavorites() {
        val path = _state.value.currentPath
        viewModelScope.launch {
            favoritesRepository.addFavorite(path, File(path).name, isDirectory = true, mimeType = "")
        }
    }

    fun removeCurrentFolderFromFavorites() {
        val path = _state.value.currentPath
        viewModelScope.launch {
            favoritesRepository.removeFavorite(path)
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

    /**
     * Moves or copies exactly [file], leaving the selection untouched.
     *
     * A row's own menu and its swipe buttons act on that row alone, which the selection-based
     * entry points below cannot express: reaching them by first calling [toggleSelection] made the
     * row menu operate on everything already selected plus the tapped row, with nothing between the
     * tap and the operation to show a count.
     */
    fun onMoveTo(file: FileItem) = startOperation(listOf(file), OperationMode.MOVE)

    fun onCopyTo(file: FileItem) = startOperation(listOf(file), OperationMode.COPY)

    private fun onMoveTo() = startSelectionOperation(OperationMode.MOVE)

    private fun onCopyTo() = startSelectionOperation(OperationMode.COPY)

    private fun startOperation(items: List<FileItem>, mode: OperationMode) {
        _state.update {
            it.copy(
                pickerRequest = PickerRequest(
                    items = items,
                    mode = mode
                )
            )
        }
    }

    /**
     * Runs [mode] over the current selection, and leaves selection mode whether or not there was
     * anything to run it on: a selected path that no longer resolves to a listed file — deleted by
     * another app, or on a volume that was unmounted — would otherwise keep the screen counting a
     * row it cannot show until the user clears the selection by hand or the folder reloads.
     */
    private fun startSelectionOperation(mode: OperationMode) {
        val selectedItems = getSelectedFiles()

        if (selectedItems.isEmpty()) {
            clearSelection()
            return
        }

        _state.update {
            it.copy(
                pickerRequest = PickerRequest(
                    items = selectedItems,
                    mode = mode
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
        // This screen only ever opens the picker to move or copy; a null mode means the picker was
        // opened to choose a folder, which happens from the settings screen instead.
        val mode = request.mode ?: return
        dismissPicker()

        operationJob = viewModelScope.launch {
            try {
                val (totalSize, availableBytes) = withContext(ioDispatcher) {
                    val size = fileRepository.totalSize(request.items)
                    // The destination can be gone by the time the operation starts: the volume was
                    // unmounted, or another app removed or renamed the folder after the picker
                    // listed it. StatFs throws on a path it cannot stat, so treat that as a missing
                    // destination instead of letting it crash the app.
                    val available = try {
                        StatFs(targetPath).availableBytes
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    size to available
                }

                if (availableBytes == null) {
                    _events.emit(FolderUiEvent.ShowToastRes(R.string.error_invalid_target_path))
                    return@launch
                }

                if (availableBytes < totalSize) {
                    _events.emit(FolderUiEvent.ShowToastRes(R.string.error_not_enough_space))
                    return@launch
                }

                executeOperationInternal(request.items, targetPath, mode)
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

                // Paths arrive in batches while the transfer runs, so every emission is handled and
                // not just the final one — the repository keeps at most one batch and starts a new
                // one after handing it over. Empty on the emissions that only report progress,
                // which is most of them: skip those rather than pay a dispatch to the IO thread
                // notifyDeleted hops to for nothing.
                if (copyProgress.createdPaths.isNotEmpty()) {
                    MediaStoreUtil.scanFiles(context, copyProgress.createdPaths)
                }
                if (mode == OperationMode.MOVE &&
                    !copyProgress.sourceDeleteFailed &&
                    copyProgress.deletedSourcePaths.isNotEmpty()
                ) {
                    MediaStoreUtil.notifyDeleted(context, copyProgress.deletedSourcePaths)
                }

                if (copyProgress.isComplete) {
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
        } catch (_: InsufficientStorageException) {
            // The volume filled up after the pre-flight check in executeOperation (another app
            // writing, or a source that grew). Environmental, not an app bug — tell the user what
            // to fix instead of showing the generic failure toast.
            val actionName = if (mode == OperationMode.MOVE) "move" else "copy"
            AnalyticsTracker.trackDestinationPickerOperationFinished(actionName, false)
            AnalyticsTracker.trackOperationFailed(actionName, "insufficient_storage")
            _state.update { it.copy(operationProgress = null) }
            _events.emit(FolderUiEvent.ShowToastRes(R.string.error_not_enough_space))
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
                // Keep favorites/recents pointing at the renamed item (and any entries inside a
                // renamed folder); otherwise they're dropped as non-existent on return to home.
                favoritesRepository.updatePath(result.oldPath, result.newPath)
                recentFilesRepository.updatePath(result.oldPath, result.newPath)
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
                val paths = files.map { it.path }
                // Node count, not leaf files: the branch below cannot be cancelled or show
                // progress, so a selection that is slow to walk has to route to the other one
                // even when few of its nodes are files.
                val totalNodes = fileRepository.totalNodeCount(files)
                if (totalNodes < DELETE_PROGRESS_THRESHOLD) {
                    val success = fileRepository.delete(files)
                    if (success) {
                        MediaStoreUtil.notifyTreeDeleted(context, paths)
                        AnalyticsTracker.trackDeleteCompleted(itemCount, "folder")
                    } else {
                        AnalyticsTracker.trackOperationFailed("delete", "unknown")
                        _events.emit(FolderUiEvent.ShowToastRes(R.string.delete_error))
                    }
                    loadFiles()
                } else {
                    try {
                        fileRepository.deleteWithProgress(files)
                            // The flow reports every file it deletes, which for a large tree is
                            // more updates than the UI can draw: applied one by one they flood the
                            // main thread with recompositions it cannot keep up with. Conflating
                            // samples the latest instead, and still delivers the final value.
                            .conflate()
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
                                        MediaStoreUtil.notifyTreeDeleted(context, paths)
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
            } catch (_: InsufficientStorageException) {
                // The device ran out of space mid-archive. Environmental, not an app bug — the
                // partial archive is already cleaned up, so show an actionable toast but don't
                // report it to Crashlytics.
                _state.update { it.copy(compressProgress = null) }
                AnalyticsTracker.trackOperationFailed("compress", "insufficient_storage")
                _events.emit(FolderUiEvent.ShowToastRes(R.string.error_not_enough_space))
            } catch (_: DestinationNotWritableException) {
                // The OS rejected creating the archive (e.g. the folder was deleted or its volume
                // unmounted between opening it and confirming the dialog). Environmental, not an
                // app bug — show the failure toast but don't report it to Crashlytics.
                _state.update { it.copy(compressProgress = null) }
                AnalyticsTracker.trackOperationFailed("compress", "destination_not_writable")
                _events.emit(FolderUiEvent.ShowToastRes(R.string.compress_error))
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
                    fileRepository.countChildren(currentState.currentPath, currentState.showHidden) == null
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
                // A directory listing is the largest structure this screen holds, and it stays
                // held while the user scrolls it. Recorded so an OutOfMemoryError report — whose
                // stack names an unrelated allocation — shows the folder size behind it.
                ErrorReporter.setCount(KEY_FOLDER_ENTRIES, files.size)
                ErrorReporter.recordHeap()
                loadChildCounts(files, currentState.showHidden)
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
     * Loads each directory's child count off the blocking list load. Counts are taken in display
     * order; results overwrite the map in place, and entries for paths no longer present are pruned
     * up front. A null result (directory can't be read, e.g. scoped-storage folders) is stored as a
     * present-null entry, so the UI can distinguish "still loading" (absent) from "restricted"
     * (present-null). Runs as a child of [loadJob], so a new load cancels pending counts.
     *
     * [MAX_CONCURRENT_COUNTS] workers pull from the shared list rather than one coroutine being
     * launched per directory: a folder with hundreds of thousands of subdirectories would otherwise
     * queue that many coroutines on [countDispatcher] at once, which costs more than the listing they
     * describe.
     *
     * Results are buffered and folded into the published map on a [COUNT_FLUSH_INTERVAL_MS] cadence
     * rather than one at a time. Publishing means copying the whole map — a StateFlow has to hand out
     * a fresh value for the UI to see the change — so a count per update costs N copies of a map
     * growing to N entries. Quadratic short-lived allocations interleaved with the long-lived listing
     * they describe is how the heap ends up with megabytes free and no block left in it large enough
     * to list the next directory. A timer bounds the copies by how long the pass takes instead of by
     * how many directories it covers; the cost is that counts appear in bursts rather than one by one.
     *
     * [showHidden] is the value the listing in [files] was taken with, not a fresh read, so a count
     * cannot describe a folder under one filter while the rows below it were taken under another.
     *
     * Runs whichever second line the user chose: the pass is also what detects a folder the app
     * cannot read, and that has to be reported under all three options.
     */
    private fun CoroutineScope.loadChildCounts(files: List<FileItem>, showHidden: Boolean) {
        val directoryPaths = ArrayList<String>()
        for (file in files) {
            if (file.isDirectory) directoryPaths.add(file.path)
        }
        val retained = directoryPaths.toSet()
        _childCounts.update { current -> current.filterKeys { it in retained } }
        if (directoryPaths.isEmpty()) return

        val pending = HashMap<String, Int?>()
        val pendingLock = Mutex()

        // Off the main thread: the flush copies a map as large as the folder has directories.
        launch(ioDispatcher) {
            val flusher = launch {
                while (true) {
                    delay(COUNT_FLUSH_INTERVAL_MS)
                    flushChildCounts(pending, pendingLock)
                }
            }

            // Returns once every worker has drained the list, so the flusher outlives the last count.
            coroutineScope {
                val nextIndex = AtomicInteger(0)
                repeat(minOf(MAX_CONCURRENT_COUNTS, directoryPaths.size)) {
                    launch(countDispatcher) {
                        while (true) {
                            // Nothing else in this loop observes cancellation: countChildren has no
                            // suspension point by design, and an uncontended lock does not suspend
                            // either. Without this a superseded load's workers would list the old
                            // folder to its end, holding every slot of countDispatcher against the
                            // load that replaced them.
                            ensureActive()

                            val index = nextIndex.getAndIncrement()
                            if (index >= directoryPaths.size) break

                            val path = directoryPaths[index]
                            val count = fileRepository.countChildren(path, showHidden)
                            pendingLock.withLock { pending[path] = count }
                        }
                    }
                }
            }

            flusher.cancelAndJoin()
            flushChildCounts(pending, pendingLock)
        }
    }

    /**
     * Publishes the counts taken since the previous flush in a single copy of the map, and allocates
     * nothing when none arrived.
     *
     * Drains and publishes under the same lock, and checks cancellation before either. A count
     * leaves [pending] only once it has been published: the cancel that ends the flusher at the end
     * of a pass can land inside a flush already in progress, and a batch taken out of [pending]
     * first would be stranded — the final flush would find nothing, and those directories would show
     * no count until the user reloaded the folder. Checking first leaves the batch where the final
     * flush will still find it.
     *
     * The check has to be explicit because taking an uncontended lock is not a suspension point.
     * Without it a superseded load's last flush would publish into a map the load that replaced it
     * has already pruned.
     */
    private suspend fun flushChildCounts(pending: MutableMap<String, Int?>, lock: Mutex) {
        lock.withLock {
            if (pending.isEmpty()) return
            currentCoroutineContext().ensureActive()
            _childCounts.update { current -> current + pending }
            pending.clear()
        }
    }

    class Factory(
        private val application: Application,
        private val path: String,
        private val title: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Drops the cached home-screen location sizes whenever this screen changes files, so a
            // card is not left reporting a pre-delete total until the cache TTL lapses.
            val locationsCacheSource = DataStoreLocationsCacheSource(application.locationsCacheDataStore)
            val fileRepository = FileRepository { locationsCacheSource.clearCache() }
            val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
            val storageRepository = StorageRepository(AndroidStorageSource(application))
            val favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(application.favoriteFilesDataStore))
            val recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(application.recentFilesDataStore))
            return FolderViewModel(
                application,
                path,
                title,
                fileRepository,
                preferencesRepository,
                storageRepository,
                favoritesRepository,
                recentFilesRepository
            ) as T
        }
    }

    companion object {
        private const val MAX_CONCURRENT_COUNTS = 12

        /**
         * How long [loadChildCounts] lets counts accumulate before publishing them. Short enough
         * that a folder's counts still fill in while the user is looking at it, long enough that a
         * folder with thousands of directories publishes on the order of ten times a second rather
         * than thousands.
         */
        private const val COUNT_FLUSH_INTERVAL_MS = 100L
        private const val DELETE_PROGRESS_THRESHOLD = 10
        private const val KEY_FOLDER_ENTRIES = "folder_entries"
    }
}
