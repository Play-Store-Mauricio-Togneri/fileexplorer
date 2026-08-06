package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
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
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.supervisorScope
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
    val pendingApkInstall: FileItem? = null,
    val pendingApkInstallSource: String = "recent"
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
    private var loadJob: Job? = null
    private var reloadPending = false

    // Deliberately does not call loadData(): the screen's repeatOnLifecycle(STARTED) effect is its
    // sole trigger, so a load happens once per visit including the first. Calling it here too
    // meant a cold start ran the whole thing twice over — every location's directory walk, every
    // recents/favorites stat — with the two passes racing each other.
    init {
        observeRecentFiles()
        observeFavorites()
        observeUncompressHandler()
    }

    // Sole source of truth for which entries uiState.recentFiles holds. Persisted changes (adds
    // from file opens, removals, deletions) flow back through here; the action methods below only
    // pre-empt this optimistically for instant feedback. loadData() must never write a snapshot of
    // its own over recentFiles, or a stale one could overwrite a just-removed entry. The single
    // exception is refreshThumbnailTimestamps(), which replaces no entries: it maps over the list
    // read inside its own update block and touches only lastModified, so it cannot resurrect one.
    private fun observeRecentFiles() {
        viewModelScope.launch {
            combine(
                recentFilesRepository.recentFilesFlow,
                preferencesRepository.recentFilesEnabled
            ) { recentFiles, enabled ->
                if (enabled) recentFiles else emptyList()
            }.flowOn(ioDispatcher).collect { recentFiles ->
                _uiState.update { it.copy(recentFiles = recentFiles) }
                refreshThumbnailTimestamps()
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
                    refreshThumbnailTimestamps()
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

    // Called once per visit by the screen's repeatOnLifecycle(STARTED) effect. Every branch below
    // is file system work — a directory walk per location, two stats per recents/favorites entry —
    // so two passes must never run at once.
    //
    // A call arriving while a pass is running is deferred, not dropped: the load survives ON_PAUSE
    // (it belongs to viewModelScope, not the effect's scope), so backgrounding mid-load, changing
    // files elsewhere and resuming before it finishes would otherwise leave the screen showing data
    // read before the change until some later resume. Deferring costs a redundant pass only when a
    // resume genuinely lands mid-load; the pass that prompted it had already read disk, so it is
    // not redundant at all.
    @MainThread
    fun loadData() {
        if (loadJob?.isActive == true) {
            reloadPending = true
            return
        }

        loadJob = viewModelScope.launch {
            do {
                // Cleared before the pass reads anything, so a call arriving during the pass is
                // always honoured. Only ever touched from the main thread: loadData() is called
                // from the lifecycle effect, and viewModelScope is Dispatchers.Main.immediate.
                reloadPending = false

                // supervisorScope, not a plain parent job: these four are independent, and before
                // the guard they were siblings under viewModelScope's own SupervisorJob. Without it
                // a failure in one would now cancel the other three.
                supervisorScope {
                    launch {
                        if (!hasLoadedOnce) {
                            _uiState.update { it.copy(isLoading = true) }
                        }

                        val (locations, storages) = withContext(ioDispatcher) {
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
                    launch {
                        recentFilesRepository.pruneNonExistentFiles()
                    }
                    launch {
                        favoritesRepository.pruneNonExistentFiles()
                    }
                    launch {
                        refreshThumbnailTimestamps()
                    }
                }
            } while (reloadPending)
        }
    }

    // Called from loadData() on every visit, and again from each observer once it has published a
    // list. The observer calls are what make it reliable: this reads _uiState directly and returns
    // early when it finds nothing to re-stat, so on a cold start the loadData() call can lose the
    // race against the recents and favorites flows — both cross flowOn(ioDispatcher) before their
    // first emission — find both lists still empty, and silently do nothing, leaving an
    // edited-in-place file on its previously decoded thumbnail until some later visit. Running it
    // again when a list actually arrives closes that window; the stores emit only when written, so
    // the extra passes are rare and bounded by MAX_RECENT_FILES plus the favorites.
    //
    // Favorites and recents carry the modification time their store stamped the last time it
    // emitted, and a store emits only when it is written. A file edited in place at the same path
    // is neither added nor removed, so that timestamp — and with it the thumbnail's memory cache
    // key (see ThumbnailCacheKey) — stays frozen and the card keeps showing the previously decoded
    // image, while the folder list, re-stat'd on every listing, shows the new one. Re-stat here so
    // the two agree. uiState only, never the store: the timestamp describes the file rather than
    // the stored entry and is deliberately not persisted. Both lists are re-read inside the update
    // block, so an entry dropped meanwhile — pruned, or removed optimistically by an action — is
    // not resurrected; only the timestamp of an entry still present is replaced.
    private suspend fun refreshThumbnailTimestamps() {
        val state = _uiState.value
        // Only cards that render a thumbnail consume the timestamp. Restat'ing the rest would pay
        // for nothing and would churn a favorited directory's entry on every resume, since its
        // modification time changes whenever a child is added or removed.
        val paths = state.favorites.filter { it.hasThumbnailSupport }.mapTo(mutableSetOf()) { it.path }
        state.recentFiles.filter { it.hasThumbnailSupport }.mapTo(paths) { it.path }
        if (paths.isEmpty()) return

        val timestamps = withContext(ioDispatcher) {
            // exists() is kept as a separate call rather than reading lastModified() == 0L as
            // "missing", matching the repositories: this app can produce a genuinely epoch-stamped
            // file when extracting an archive. A file that has vanished keeps its last known
            // timestamp here and is removed by pruneNonExistentFiles instead.
            paths.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) path to file.lastModified() else null
            }.toMap()
        }
        _uiState.update { current ->
            current.copy(
                favorites = current.favorites.map { it.copy(lastModified = timestamps[it.path] ?: it.lastModified) },
                recentFiles = current.recentFiles.map { it.copy(lastModified = timestamps[it.path] ?: it.lastModified) }
            )
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
            val deleted = fileRepository.delete(listOf(fileItem))
            if (deleted) {
                // A favorited directory's descendants are reported to MediaStore too — the
                // notification matches the path as a prefix — or media inside it is orphaned until
                // the next scan.
                MediaStoreUtil.notifyTreeDeleted(context, listOf(favorite.path))
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

    fun setPendingApkInstall(file: FileItem?, source: String) {
        _uiState.update { it.copy(pendingApkInstall = file, pendingApkInstallSource = source) }
    }

    fun clearPendingApkInstall() {
        _uiState.update { it.copy(pendingApkInstall = null) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
            val locationsCacheSource = DataStoreLocationsCacheSource(application.locationsCacheDataStore)
            return HomeViewModel(
                application = application,
                recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(application.recentFilesDataStore)),
                favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(application.favoriteFilesDataStore)),
                locationsRepository = LocationsRepository(locationsCacheSource, preferencesRepository),
                storageRepository = StorageRepository(AndroidStorageSource(application)),
                preferencesRepository = preferencesRepository,
                // Drops the cached location sizes whenever this screen changes files itself, so a
                // card is not left reporting a pre-delete total until the cache TTL lapses.
                fileRepository = FileRepository { locationsCacheSource.clearCache() }
            ) as T
        }
    }
}
