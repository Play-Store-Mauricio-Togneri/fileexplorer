package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
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
import com.mauriciotogneri.fileexplorer.data.source.AndroidMediaChangeSource
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageVolumeChangeSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.source.MediaChangeSource
import com.mauriciotogneri.fileexplorer.data.source.StorageVolumeChangeSource
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.deleteFailureFor
import com.mauriciotogneri.fileexplorer.data.util.reportableErrno
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val sectionOrder: List<HomeSection> = HomeSection.DEFAULT_ORDER,
    val selectedRecentFile: RecentFile? = null,
    val selectedRecentFileIsDirectory: Boolean = false,
    val recentFileMode: String = "icon",
    val recentFileToDelete: RecentFile? = null,
    val selectedFavorite: Favorite? = null,
    val selectedFavoriteIsDirectory: Boolean = false,
    val favoriteFileMode: String = "icon",
    val favoriteToDelete: Favorite? = null,
    /**
     * The message a failed delete has yet to show, or null. A resource id rather than the boolean
     * this was, so that the reason survives to the toast — the delete paths know which errno
     * stopped them and had no way to say so.
     */
    @param:StringRes val deleteErrorResId: Int? = null,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null,
    val pendingApkInstall: FileItem? = null,
    val pendingApkInstallSource: String = "recent"
) {
    /**
     * Whether [section] has anything to show. Mirrors each section composable's own empty check,
     * which stays in place: this decides the separators between sections, not what a section draws.
     */
    fun hasContent(section: HomeSection): Boolean = when (section) {
        HomeSection.RECENT -> recentFiles.isNotEmpty()
        HomeSection.FAVORITES -> favorites.isNotEmpty()
        HomeSection.LOCATIONS -> locations.isNotEmpty()
        HomeSection.STORAGE -> storages.isNotEmpty()
    }
}

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
    private val mediaChangeSource: MediaChangeSource,
    private val storageVolumeChangeSource: StorageVolumeChangeSource,
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

    val showAnalyzerBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_ANALYZER)
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

    /**
     * The sections that have something to show, in the order the user arranged them — everything
     * the home screen needs to lay itself out, including where the separators between sections go.
     *
     * Emitted rather than derived in the composable so the list keeps one identity per arrangement:
     * filtering on each recomposition would hand Compose a new list every time any unrelated part of
     * [uiState] changed.
     *
     * Eager, unlike the badge flows: those subscribe to the preferences store and are worth stopping
     * when nothing is looking, while this only filters four entries of state that is already hot. In
     * exchange the value is correct before the first collector arrives, so the screen's first frame
     * reads the real arrangement rather than an empty list it would immediately replace.
     */
    val visibleSections: StateFlow<List<HomeSection>> = uiState
        .map { state -> state.sectionOrder.filter { section -> state.hasContent(section) } }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HomeUiState().let { initial ->
                initial.sectionOrder.filter { section -> initial.hasContent(section) }
            }
        )

    private var hasLoadedOnce = false
    private var loadJob: Job? = null
    private var reloadPending = false

    // Whether the deferred reload [reloadPending] stands for should prune. Main-thread-only, for
    // the reason [reloadPending] is.
    private var prunePending = false

    // Deliberately does not call loadData(): the screen's repeatOnLifecycle(STARTED) effect is what
    // triggers the first one, so a load happens once per visit including the first. Calling it here
    // too meant a cold start ran the whole thing twice over — every location's directory walk,
    // every recents/favorites stat — with the two passes racing each other. The delete paths below
    // trigger it as well, but only in response to a change they just made.
    init {
        observeRecentFiles()
        observeFavorites()
        observeSectionOrder()
        observeUncompressHandler()
        observeMediaChanges()
        observeStorageVolumeChanges()
    }

    /**
     * Notes that shared storage changed outside this app, so the next load measures the trees
     * again. FileRepository's hook covers only what this app itself did, and without this one the
     * cache TTL is the sole backstop, leaving a card reporting a pre-change total for up to
     * CACHE_DURATION_MS after a camera shot, a download, or another file manager's delete.
     *
     * Marks without loading. The mark costs nothing to repeat, which matters because every app on
     * the device can publish these — one per file during someone else's bulk copy — and because
     * this collector outlives the screen being visible, belonging to viewModelScope rather than to
     * the lifecycle effect. Repeating the load would instead walk every location for a screen
     * nobody is looking at; every return to it already crosses ON_START and loads.
     *
     * The observer fires for this app's own operations too, which FileRepository has already
     * accounted for. Marking again is free, and telling the two apart is not something the
     * provider reliably lets a caller do.
     */
    private fun observeMediaChanges() {
        viewModelScope.launch {
            mediaChangeSource.changes().collect { locationsRepository.markSizeCacheStale() }
        }
    }

    /**
     * Reloads when a volume is mounted or goes away, so that a card inserted while this screen is
     * on top appears on it, and one pulled out stops being offered.
     *
     * Loads where [observeMediaChanges] only marks, because the two are not the same kind of event.
     * A media notification arrives once per file and every app on the device can publish a burst of
     * them, so loading on each would walk every location for someone else's bulk copy. A volume
     * broadcast arrives when a person puts a card in the slot: rare, and the thing it changes —
     * which volumes exist — is read by nothing but a load. [loadData] already folds a call arriving
     * mid-pass into a single follow-up, so the unmount-then-mount pair one insertion publishes
     * costs two passes at most.
     *
     * Belongs to viewModelScope rather than to the screen's lifecycle: a volume that appears while
     * the screen is backgrounded is picked up by the ON_START load on the way back either way, and
     * scoping it here keeps it beside the other observers with nothing to unregister on the way out.
     */
    private fun observeStorageVolumeChanges() {
        viewModelScope.launch {
            storageVolumeChangeSource.changes().collect { loadData(prune = false) }
        }
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

    // Sole source of truth for uiState.sectionOrder. Reordering is persisted from the settings
    // screen, so the change arrives here rather than through an action on this ViewModel.
    private fun observeSectionOrder() {
        viewModelScope.launch {
            preferencesRepository.homeSectionOrder
                .flowOn(ioDispatcher)
                .collect { order -> _uiState.update { it.copy(sectionOrder = order) } }
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

    fun dismissAnalyzerBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_ANALYZER)
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
    fun loadData() = loadData(prune = true)

    /**
     * [prune] is false only for a load a volume change asked for. Pruning answers "is this file
     * still there" with `File.exists()`, and an unmount is precisely the moment that question
     * returns the wrong answer for every path on the volume: the entries are not gone, the volume
     * is. The prune writes that answer back to the store, so pruning on a volume change would
     * delete the user's favorites and recents on a card they merely ejected, and putting the card
     * back would not bring them back.
     *
     * What suppressing the write leaves on screen is the cards themselves: both flows do filter
     * non-existent files, but they only re-run that filter when the store is written, and this pass
     * writes nothing. So an ejected card's favorites stay visible until something else writes —
     * which is what the screen already did before any of this existed, since nothing reacted to an
     * eject at all. Stale entries are the state this app is built to expect; a permanent delete of
     * entries whose volume is merely absent is not, and only one of the two can be undone by
     * putting the card back. The next lifecycle load prunes for real once the volumes have settled.
     */
    @MainThread
    private fun loadData(prune: Boolean) {
        if (loadJob?.isActive == true) {
            reloadPending = true
            // Sticky across the deferral: the follow-up pass is the one pass that answers for every
            // call folded into it, so it must prune if any of them asked for it. Without this an
            // ON_START load landing mid-pass would silently lose its prune to a volume change.
            prunePending = prunePending || prune
            return
        }

        var prunesThisPass = prune

        loadJob = viewModelScope.launch {
            do {
                // Cleared before the pass reads anything, so a call arriving during the pass is
                // always honoured. Only ever touched from the main thread: loadData() is called
                // from the lifecycle effect, and viewModelScope is Dispatchers.Main.immediate.
                reloadPending = false
                prunePending = false

                // supervisorScope, not a plain parent job: these four are independent, and before
                // the guard they were siblings under viewModelScope's own SupervisorJob. Without it
                // a failure in one would now cancel the other three.
                supervisorScope {
                    // One enumeration for the whole pass. The cards and the prune both need the
                    // mounted volumes, and asking twice would not only cost a second enumeration
                    // but let the two halves disagree: a card unmounting between the two calls
                    // would leave the prune deciding against a volume list the cards never showed.
                    // async in a supervisorScope, so a failure surfaces at each await() rather than
                    // taking the siblings down with it.
                    val storagesAsync = async(ioDispatcher) { storageRepository.getStorages() }

                    launch {
                        if (!hasLoadedOnce) {
                            _uiState.update { it.copy(isLoading = true) }
                        }

                        val locations = withContext(ioDispatcher) {
                            locationsRepository.getLocations()
                        }
                        val storages = storagesAsync.await()

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
                    //
                    // Which volumes are mounted is what tells a deleted file apart from an ejected
                    // card, so both prunes wait for that list and share the one snapshot. Its own
                    // launch rather than the one above: that one also waits on getLocations(),
                    // which walks directory trees, and pruning has no reason to queue behind it.
                    // getStorages() failing means neither prune runs, which is the safe direction
                    // for the store, since the only thing they do is delete. It is not caught here
                    // and is not contained: the failure leaves this launch exactly the way it left
                    // the single call this replaced, which is what the card update above still does
                    // with it too.
                    //
                    // The snapshot can also be stale rather than absent — a card pulled after the
                    // enumeration and before the exists() calls below is still listed as mounted,
                    // and its entries are forgotten. That window is tens of milliseconds wide
                    // against an unconditional prune on every load before this, so it narrows the
                    // loss rather than closing it.
                    if (prunesThisPass) {
                        launch {
                            val mountedRoots = storagesAsync.await().map { it.path }

                            // Nested, so that one prune failing does not cancel the other, exactly
                            // as the outer supervisorScope keeps these four apart.
                            supervisorScope {
                                launch {
                                    recentFilesRepository.pruneNonExistentFiles(mountedRoots)
                                }
                                launch {
                                    favoritesRepository.pruneNonExistentFiles(mountedRoots)
                                }
                            }
                        }
                    }
                    launch {
                        refreshThumbnailTimestamps()
                    }
                }

                prunesThisPass = prunePending
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
            // The type is read in the same stat as exists() and handed to the sheet, which decides
            // from it which actions to offer. RecentFile.isDirectory is a constant false and the
            // store re-validates a stored path with exists() alone, which a directory satisfies, so
            // the sheet would otherwise offer Open with and Share on a directory the card's tap
            // handler already navigates into (see openRecentFile in HomeScreen).
            val (fileExists, isDirectory) = withContext(ioDispatcher) {
                File(recentFile.path).let { it.exists() to it.isDirectory }
            }
            if (!fileExists) {
                recentFilesRepository.removeRecentFile(recentFile.path)
                _uiState.update { state ->
                    state.copy(recentFiles = state.recentFiles.filter { it.path != recentFile.path })
                }
                _events.emit(HomeUiEvent.ShowToast(R.string.recent_file_not_found))
            } else {
                _uiState.update {
                    it.copy(
                        selectedRecentFile = recentFile,
                        selectedRecentFileIsDirectory = isDirectory,
                        recentFileMode = mode
                    )
                }
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
                val isDirectory = file.isDirectory
                FileItem(
                    path = recentFile.path,
                    name = recentFile.name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0 else file.length(),
                    lastModified = file.lastModified(),
                    createdTime = file.lastModified(),
                    mimeType = recentFile.mimeType
                )
            }
            // Stat'd above rather than hardcoded false: a recents entry is a file by contract —
            // RecentFile.isDirectory is a constant and addRecentFile refuses directories — but the
            // store records no type of its own, and getRecentFiles re-validates the stored path with
            // exists() alone, which a directory satisfies. So a directory here is something that took the path over
            // after the entry was recorded, and delete decides recursion from a live stat of its
            // own: it would walk the whole tree behind a dialog that named one item, and
            // notifyDeleted below is the file-only variant, so every descendant's MediaStore row
            // would outlive it. Nothing was touched, so the card reload at the end is skipped too.
            if (fileItem.isDirectory) {
                AnalyticsTracker.trackOperationFailed(
                    operation = "delete",
                    errorType = "path_type_changed",
                    source = "home_recent",
                    outcome = "all_failed"
                )
                _uiState.update {
                    it.copy(recentFileToDelete = null, deleteErrorResId = R.string.delete_error)
                }
                return@launch
            }
            val result = fileRepository.delete(listOf(fileItem))
            if (result.success) {
                // Reported deleted only if this app emptied the path; an entry whose file
                // something else already removed is scanned instead, so a path taken over since
                // keeps its file. A recents entry is re-validated with exists() alone, so it goes
                // stale between the list and the tap more often than anything else here.
                if (result.removedPaths.isNotEmpty()) {
                    MediaStoreUtil.notifyDeleted(context, result.removedPaths)
                }
                MediaStoreUtil.scanFiles(context, result.alreadyAbsentPaths)
                recentFilesRepository.removeRecentFile(recentFile.path)
                AnalyticsTracker.trackDeleteCompleted(
                    1,
                    "home_recent",
                    removedCount = result.removedPaths.size,
                    alreadyAbsentCount = result.alreadyAbsentPaths.size
                )
                _uiState.update { state ->
                    state.copy(
                        recentFiles = state.recentFiles.filter { it.path != recentFile.path },
                        recentFileToDelete = null
                    )
                }
            } else {
                val failure = deleteFailureFor(result.failureErrno)
                AnalyticsTracker.trackOperationFailed(
                    operation = "delete",
                    errorType = failure.analyticsLabel,
                    errno = reportableErrno(result.failureErrno),
                    source = "home_recent",
                    outcome = "all_failed"
                )
                _uiState.update {
                    it.copy(recentFileToDelete = null, deleteErrorResId = failure.messageResId)
                }
            }

            // The delete just invalidated every cached location size (FileRepository's
            // onFilesMutated hook, wired in Factory below), but clearing the cache only decides
            // what the next pass measures — it does not start one. The screen's
            // repeatOnLifecycle(STARTED) effect is what would otherwise start it, and deleting
            // from here never leaves the screen, so without this the location and storage cards
            // keep reporting pre-delete totals until the user navigates away and back. This also
            // moves the walk off the resume path, which is where it used to land.
            //
            // Outside the branch, as in FolderViewModel: recents are files-only, so a false here
            // means nothing was removed, but the same call covers a delete that failed because the
            // file had already vanished — the entry is pruned rather than left pointing at nothing.
            //
            // Safe to call unconditionally. It is @MainThread and this resumes on Main; a pass
            // already running is deferred rather than joined by the loadJob guard, so deletes
            // arriving during one collapse into a single follow-up pass instead of queueing.
            loadData()
        }
    }

    fun dismissDeleteError() {
        _uiState.update { it.copy(deleteErrorResId = null) }
    }

    // ---------- Favorites ---------- \\

    fun showFavoriteActions(favorite: Favorite, mode: String) {
        viewModelScope.launch {
            // Stat'd alongside exists() for the reason showRecentFileActions above spells out.
            // Favorite.isDirectory records the type the entry had when it was added, and getFavorites
            // re-validates with exists() alone, so the stored flag is the one thing the sheet must
            // not classify the entry by.
            val (fileExists, isDirectory) = withContext(ioDispatcher) {
                File(favorite.path).let { it.exists() to it.isDirectory }
            }
            if (!fileExists) {
                favoritesRepository.removeFavorite(favorite.path)
                _uiState.update { state ->
                    state.copy(favorites = state.favorites.filter { it.path != favorite.path })
                }
                _events.emit(HomeUiEvent.ShowToast(R.string.recent_file_not_found))
            } else {
                _uiState.update {
                    it.copy(
                        selectedFavorite = favorite,
                        selectedFavoriteIsDirectory = isDirectory,
                        favoriteFileMode = mode
                    )
                }
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
                val isDirectory = file.isDirectory
                FileItem(
                    path = favorite.path,
                    name = favorite.name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0 else file.length(),
                    lastModified = file.lastModified(),
                    createdTime = file.lastModified(),
                    mimeType = favorite.mimeType
                )
            }
            // A favorite may legitimately be a directory, so the test is not directory-ness but the
            // one direction that loses data: an entry the dialog described as a file whose path a
            // directory now occupies. getFavorites re-validates a stored path with exists() alone,
            // which a directory satisfies, so the two can disagree — and delete decides recursion
            // from a live stat of its own, walking the whole tree behind a dialog that named one
            // item. The opposite drift is left alone: a favorited directory that is now a file
            // deletes as it always did, and one that has vanished now counts as deleted — a path
            // that already holds nothing satisfies a delete — so the entry is pruned rather than
            // left pointing at nothing behind an error the user can do nothing about.
            if (fileItem.isDirectory && !favorite.isDirectory) {
                AnalyticsTracker.trackOperationFailed(
                    operation = "delete",
                    errorType = "path_type_changed",
                    source = "home_favorite",
                    outcome = "all_failed"
                )
                _uiState.update {
                    it.copy(favoriteToDelete = null, deleteErrorResId = R.string.delete_error)
                }
                return@launch
            }
            val result = fileRepository.delete(listOf(fileItem))
            if (result.success) {
                // A favorited directory's descendants are reported to MediaStore too — the
                // notification matches the path as a prefix — or media inside it is orphaned until
                // the next scan. That prefix is also why a path this app did not empty must not go
                // through here: it would take every live file under it. Those are scanned instead.
                if (result.removedPaths.isNotEmpty()) {
                    MediaStoreUtil.notifyTreeDeleted(context, result.removedPaths)
                }
                MediaStoreUtil.scanFiles(context, result.alreadyAbsentPaths)
                favoritesRepository.removeFavorite(favorite.path)
                AnalyticsTracker.trackDeleteCompleted(
                    1,
                    "home_favorite",
                    removedCount = result.removedPaths.size,
                    alreadyAbsentCount = result.alreadyAbsentPaths.size
                )
                _uiState.update { state ->
                    state.copy(
                        favorites = state.favorites.filter { it.path != favorite.path },
                        favoriteToDelete = null
                    )
                }
            } else {
                val failure = deleteFailureFor(result.failureErrno)
                AnalyticsTracker.trackOperationFailed(
                    operation = "delete",
                    errorType = failure.analyticsLabel,
                    errno = reportableErrno(result.failureErrno),
                    source = "home_favorite",
                    outcome = "all_failed"
                )
                _uiState.update {
                    it.copy(favoriteToDelete = null, deleteErrorResId = failure.messageResId)
                }
            }

            // Recomputes the location and storage cards, for the reason confirmDeleteRecentFile
            // above spells out. It matters more here: a favorite can be a directory, so a single
            // delete can move a card's total by the whole subtree — and unlike recents this one
            // can fail part-way, leaving a tree that really did shrink. Outside the branch for
            // that case, which is where a stale total is most visible.
            loadData()
        }
    }

    // Favorite toggle exposed in the Recents bottom sheet. The type is stat'd rather than taken
    // from RecentFile.isDirectory, which is a constant false: getRecentFiles re-validates a stored
    // path with exists() alone, which a directory satisfies, so a directory can occupy the path an
    // entry recorded as a file. Storing it as one leaves a favorite the card draws with a file icon
    // — and, for a thumbnail-capable mimeType, a thumbnail attempt on a directory — and whose delete
    // confirmDeleteFavorite then refuses for as long as the directory is there: its
    // path_type_changed guard is exactly this disagreement.
    //
    // Re-stat'd rather than read from selectedRecentFileIsDirectory so the write does not depend on
    // the sheet still being open when it runs, matching confirmDeleteRecentFile and
    // confirmDeleteFavorite, which each stat for themselves. A path that vanished meanwhile reads
    // as a file and is stored as one, as it was before; the next load prunes it.
    fun addRecentToFavorites(recentFile: RecentFile) {
        viewModelScope.launch {
            val isDirectory = withContext(ioDispatcher) { File(recentFile.path).isDirectory }
            favoritesRepository.addFavorite(
                recentFile.path,
                recentFile.name,
                isDirectory,
                // A favorited directory carries an empty mimeType, as FolderViewModel stores one.
                if (isDirectory) "" else recentFile.mimeType
            )
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
                fileRepository = FileRepository { locationsCacheSource.clearCache() },
                // Does the same for the changes this app did not make.
                mediaChangeSource = AndroidMediaChangeSource(application),
                // Neither of the above reports a volume arriving or leaving, only writes within the
                // ones already mounted.
                storageVolumeChangeSource = AndroidStorageVolumeChangeSource(application)
            ) as T
        }
    }
}
