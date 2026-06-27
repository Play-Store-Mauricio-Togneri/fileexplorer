package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val recentFiles: List<RecentFile> = emptyList(),
    val favorites: List<Favorite> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
    val locations: List<Location> = emptyList(),
    val storages: List<StorageDevice> = emptyList(),
    val selectedRecentFile: RecentFile? = null,
    val recentFileMode: String = "icon",
    val recentFileToDelete: RecentFile? = null,
    val selectedFavorite: Favorite? = null,
    val favoriteFileMode: String = "icon",
    val favoriteToDelete: Favorite? = null,
    val showDeleteError: Boolean = false,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null,
    val pendingApkInstall: FileItem? = null
)

@Immutable
sealed class HomeUiEvent {
    @Immutable
    data class ShowToast(val messageResId: Int) : HomeUiEvent()
}

class HomeViewModel(
    application: Application,
    private val recentFilesRepository: RecentFilesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val locationsRepository: LocationsRepository,
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val fileRepository: FileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    private var currentUncompressTarget: String = ""

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { currentUncompressTarget },
        getAllowedRoots = { storageRepository.getStorages().map { it.path } }
    )

    val showMenuBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_MENU_DRAWER)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showSettingsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_SETTINGS)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showFeedbackBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_FEEDBACK)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAboutBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_ABOUT)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var hasLoadedOnce = false

    init {
        loadData()
        observeRecentFiles()
        observeFavorites()
        observeUncompressHandler()
    }

    // Sole source of truth for uiState.recentFiles. Persisted changes (adds from file opens,
    // removals, deletions) flow back through here; the action methods below only pre-empt this
    // optimistically for instant feedback. loadData() must never write recentFiles, or a stale
    // snapshot could overwrite a just-removed entry.
    private fun observeRecentFiles() {
        viewModelScope.launch {
            combine(
                recentFilesRepository.recentFilesFlow,
                preferencesRepository.recentFilesEnabled
            ) { recentFiles, enabled ->
                if (enabled) recentFiles else emptyList()
            }.flowOn(ioDispatcher).collect { recentFiles ->
                _uiState.update { it.copy(recentFiles = recentFiles) }
            }
        }
    }

    // Sole source of truth for uiState.favorites. Persisted changes flow back through here; the
    // action methods below only pre-empt this optimistically for instant feedback. Unlike recents
    // there is no preference gate — favorites are always shown when present. favoritePaths is kept
    // alongside so the Recents sheet can show the correct Add/Remove favorite label.
    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.favoritesFlow
                .flowOn(ioDispatcher)
                .collect { favorites ->
                    _uiState.update {
                        it.copy(
                            favorites = favorites,
                            favoritePaths = favorites.mapTo(mutableSetOf()) { fav -> fav.path }
                        )
                    }
                }
        }
    }

    private fun observeUncompressHandler() {
        viewModelScope.launch {
            uncompressHandler.state.collect { uncompressState ->
                _uiState.update {
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
                        _events.emit(HomeUiEvent.ShowToast(event.messageResId))
                    }
                    is UncompressEvent.ExtractionComplete -> {
                        // Recent files don't need to refresh
                    }
                }
            }
        }
    }

    fun dismissMenuBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_MENU_DRAWER)
        }
    }

    fun dismissSettingsBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_SETTINGS)
        }
    }

    fun dismissFeedbackBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_FEEDBACK)
        }
    }

    fun dismissAboutBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_ABOUT)
        }
    }

    fun loadData() {
        viewModelScope.launch {
            if (!hasLoadedOnce) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val (locations, storages) = withContext(ioDispatcher) {
                locationsRepository.refreshSizeCache()
                Pair(
                    locationsRepository.getLocations(),
                    storageRepository.getStorages()
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    locations = locations,
                    storages = storages
                )
            }
            hasLoadedOnce = true
        }

        // Files may have been deleted while away from this screen (e.g. in a folder). Pruning
        // persists the removal, which flows back through observeRecentFiles (the sole source of
        // truth for recentFiles); it only removes missing entries, so it cannot resurrect a
        // just-removed file or clobber an optimistic update.
        viewModelScope.launch {
            recentFilesRepository.pruneNonExistentFiles()
        }
        viewModelScope.launch {
            favoritesRepository.pruneNonExistentFiles()
        }
    }

    fun showRecentFileActions(recentFile: RecentFile, mode: String) {
        viewModelScope.launch {
            val fileExists = withContext(ioDispatcher) {
                File(recentFile.path).exists()
            }
            if (!fileExists) {
                recentFilesRepository.removeRecentFile(recentFile.path)
                _uiState.update { state ->
                    state.copy(recentFiles = state.recentFiles.filter { it.path != recentFile.path })
                }
                _events.emit(HomeUiEvent.ShowToast(R.string.recent_file_not_found))
            } else {
                _uiState.update { it.copy(selectedRecentFile = recentFile, recentFileMode = mode) }
            }
        }
    }

    fun dismissRecentFileActions() {
        _uiState.update { it.copy(selectedRecentFile = null) }
    }

    fun removeFromRecents(recentFile: RecentFile) {
        viewModelScope.launch {
            recentFilesRepository.removeRecentFile(recentFile.path)
            AnalyticsTracker.trackRecentFileRemoved()
            _uiState.update { state ->
                state.copy(
                    recentFiles = state.recentFiles.filter { it.path != recentFile.path },
                    selectedRecentFile = null
                )
            }
        }
    }

    fun showDeleteConfirmation(recentFile: RecentFile) {
        _uiState.update { it.copy(recentFileToDelete = recentFile, selectedRecentFile = null) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(recentFileToDelete = null) }
    }

    fun confirmDeleteRecentFile() {
        val recentFile = _uiState.value.recentFileToDelete ?: return
        viewModelScope.launch {
            val file = File(recentFile.path)
            val fileItem = withContext(ioDispatcher) {
                FileItem(
                    path = recentFile.path,
                    name = recentFile.name,
                    isDirectory = false,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    createdTime = file.lastModified(),
                    mimeType = recentFile.mimeType
                )
            }
            val deleted = fileRepository.delete(listOf(fileItem))
            if (deleted) {
                MediaStoreUtil.notifyDeleted(context, listOf(recentFile.path))
                recentFilesRepository.removeRecentFile(recentFile.path)
                AnalyticsTracker.trackDeleteCompleted(1, "home_recent")
                _uiState.update { state ->
                    state.copy(
                        recentFiles = state.recentFiles.filter { it.path != recentFile.path },
                        recentFileToDelete = null
                    )
                }
            } else {
                AnalyticsTracker.trackOperationFailed("delete", "unknown")
                _uiState.update { it.copy(recentFileToDelete = null, showDeleteError = true) }
            }
        }
    }

    fun dismissDeleteError() {
        _uiState.update { it.copy(showDeleteError = false) }
    }

    // ---------- Favorites ---------- \\

    fun showFavoriteActions(favorite: Favorite, mode: String) {
        viewModelScope.launch {
            val fileExists = withContext(ioDispatcher) {
                File(favorite.path).exists()
            }
            if (!fileExists) {
                favoritesRepository.removeFavorite(favorite.path)
                _uiState.update { state ->
                    state.copy(favorites = state.favorites.filter { it.path != favorite.path })
                }
                _events.emit(HomeUiEvent.ShowToast(R.string.recent_file_not_found))
            } else {
                _uiState.update { it.copy(selectedFavorite = favorite, favoriteFileMode = mode) }
            }
        }
    }

    fun dismissFavoriteActions() {
        _uiState.update { it.copy(selectedFavorite = null) }
    }

    fun removeFromFavorites(favorite: Favorite) {
        viewModelScope.launch {
            favoritesRepository.removeFavorite(favorite.path)
            AnalyticsTracker.trackFavoriteRemoved()
            _uiState.update { state ->
                state.copy(
                    favorites = state.favorites.filter { it.path != favorite.path },
                    selectedFavorite = null
                )
            }
        }
    }

    fun showFavoriteDeleteConfirmation(favorite: Favorite) {
        _uiState.update { it.copy(favoriteToDelete = favorite, selectedFavorite = null) }
    }

    fun dismissFavoriteDeleteConfirmation() {
        _uiState.update { it.copy(favoriteToDelete = null) }
    }

    fun confirmDeleteFavorite() {
        val favorite = _uiState.value.favoriteToDelete ?: return
        viewModelScope.launch {
            val file = File(favorite.path)
            val fileItem = withContext(ioDispatcher) {
                FileItem(
                    path = favorite.path,
                    name = favorite.name,
                    isDirectory = favorite.isDirectory,
                    size = if (favorite.isDirectory) 0 else file.length(),
                    lastModified = file.lastModified(),
                    createdTime = file.lastModified(),
                    mimeType = favorite.mimeType
                )
            }
            // Enumerate descendants before deleting: a favorited directory's children must be
            // reported to MediaStore too, or media inside it is orphaned until the next scan.
            // collected before the delete since paths can't be walked afterwards; for a file
            // favorite this is just the single path.
            val deletedPaths = fileRepository.collectAllPaths(listOf(fileItem))
            val deleted = fileRepository.delete(listOf(fileItem))
            if (deleted) {
                MediaStoreUtil.notifyDeleted(context, deletedPaths)
                favoritesRepository.removeFavorite(favorite.path)
                AnalyticsTracker.trackDeleteCompleted(1, "home_favorite")
                _uiState.update { state ->
                    state.copy(
                        favorites = state.favorites.filter { it.path != favorite.path },
                        favoriteToDelete = null
                    )
                }
            } else {
                AnalyticsTracker.trackOperationFailed("delete", "unknown")
                _uiState.update { it.copy(favoriteToDelete = null, showDeleteError = true) }
            }
        }
    }

    // Favorite toggle exposed in the Recents bottom sheet. Recents are files-only, so isDirectory
    // is always false here.
    fun addRecentToFavorites(recentFile: RecentFile) {
        viewModelScope.launch {
            favoritesRepository.addFavorite(recentFile.path, recentFile.name, false, recentFile.mimeType)
        }
    }

    fun removeRecentFromFavorites(recentFile: RecentFile) {
        viewModelScope.launch {
            favoritesRepository.removeFavorite(recentFile.path)
        }
    }

    fun showUncompressDialog(file: FileItem) {
        currentUncompressTarget = file.parentPath
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
        _uiState.update { it.copy(pendingApkInstall = file) }
    }

    fun clearPendingApkInstall() {
        _uiState.update { it.copy(pendingApkInstall = null) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
            return HomeViewModel(
                application = application,
                recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(application.recentFilesDataStore)),
                favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(application.favoriteFilesDataStore)),
                locationsRepository = LocationsRepository(DataStoreLocationsCacheSource(application.locationsCacheDataStore), preferencesRepository),
                storageRepository = StorageRepository(AndroidStorageSource(application)),
                preferencesRepository = preferencesRepository,
                fileRepository = FileRepository()
            ) as T
        }
    }
}
