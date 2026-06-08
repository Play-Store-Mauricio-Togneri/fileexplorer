package com.mauriciotogneri.fileexplorer.ui.screens.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
sealed class SearchUiEvent {
    @Immutable
    data class ShowToastRes(val messageResId: Int) : SearchUiEvent()
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    application: Application,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events: SharedFlow<SearchUiEvent> = _events.asSharedFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var currentUncompressTarget: String = ""
    private var hasTrackedTypingStarted = false
    private var hiddenFilterTouched = false

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { currentUncompressTarget },
        getAllowedRoots = { storageRepository.getStorages().map { it.path } }
    )

    init {
        // Seed the Hidden filter from the global "show hidden files" preference. This is a
        // session-local override: toggling Hidden in search never writes back to the setting.
        // Skip if the user already changed Hidden (the async read must not clobber their choice),
        // and re-run an in-flight search if seeding actually flips the value.
        viewModelScope.launch {
            val showHidden = preferencesRepository.showHidden.first()
            if (!hiddenFilterTouched && showHidden != _uiState.value.filters.includeHidden) {
                _uiState.update { it.copy(filters = it.filters.copy(includeHidden = showHidden)) }
                if (_uiState.value.query.isNotBlank()) {
                    restartSearch()
                }
            }
        }
        queryFlow
            .debounce(DEBOUNCE_DELAY)
            .distinctUntilChanged()
            .onEach { query -> performSearch(query) }
            .launchIn(viewModelScope)
        observeUncompressHandler()
    }

    private fun observeUncompressHandler() {
        viewModelScope.launch {
            uncompressHandler.state.collect { uncompressState ->
                _uiState.value = _uiState.value.copy(
                    itemToUncompress = uncompressState.itemToUncompress,
                    uncompressEntryCount = uncompressState.entryCount,
                    isPasswordProtected = uncompressState.isPasswordProtected,
                    uncompressProgress = uncompressState.progress
                )
            }
        }
        viewModelScope.launch {
            uncompressHandler.events.collect { event ->
                when (event) {
                    is UncompressEvent.ShowToast -> {
                        _events.emit(SearchUiEvent.ShowToastRes(event.messageResId))
                    }
                    is UncompressEvent.ExtractionComplete -> {
                        // Search results don't need to refresh - the extracted files
                        // are in a different location
                    }
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        if (query == _uiState.value.query) return
        val wasEmpty = _uiState.value.query.isEmpty()
        _uiState.value = _uiState.value.copy(
            query = query,
            searchComplete = false
        )
        queryFlow.value = query
        if (wasEmpty && query.isNotEmpty() && !hasTrackedTypingStarted) {
            hasTrackedTypingStarted = true
            AnalyticsTracker.trackSearchTypingStarted()
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        searchJob = null
        queryFlow.value = ""
        _uiState.update { SearchUiState(filters = it.filters) }
    }

    fun setItemKind(kind: SearchItemKind) {
        if (kind == _uiState.value.filters.itemKind) return
        _uiState.update { it.copy(filters = it.filters.copy(itemKind = kind)) }
        AnalyticsTracker.trackSearchFilterKindChanged(kind.name.lowercase())
        restartSearch()
    }

    fun setIncludeHidden(includeHidden: Boolean) {
        hiddenFilterTouched = true
        if (includeHidden == _uiState.value.filters.includeHidden) return
        _uiState.update { it.copy(filters = it.filters.copy(includeHidden = includeHidden)) }
        AnalyticsTracker.trackSearchFilterHiddenToggled(includeHidden)
        restartSearch()
    }

    fun toggleType(type: SearchFileType) {
        val current = _uiState.value.filters.selectedTypes
        val selected = type !in current
        val updated = if (selected) current + type else current - type
        _uiState.update { it.copy(filters = it.filters.copy(selectedTypes = updated)) }
        AnalyticsTracker.trackSearchFilterTypeChanged(type.name.lowercase(), selected)
        restartSearch()
    }

    /** Selecting "All types": clears the type filter so every file type matches again. */
    fun clearTypes() {
        if (_uiState.value.filters.selectedTypes.isEmpty()) return
        _uiState.update { it.copy(filters = it.filters.copy(selectedTypes = emptySet())) }
        restartSearch()
    }

    private fun restartSearch() {
        performSearch(_uiState.value.query)
    }

    fun trackClearInputTapped() {
        AnalyticsTracker.trackSearchClearInputTapped()
    }

    override fun onCleared() {
        super.onCleared()
        if (!hasTrackedTypingStarted) {
            AnalyticsTracker.trackSearchCloseWithoutTyping()
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    query = query,
                    results = emptyList(),
                    isSearching = false,
                    searchComplete = false
                )
            }
            return
        }

        val filters = _uiState.value.filters

        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    searchComplete = false,
                    results = emptyList()
                )
            }

            val storages = storageRepository.getStorages()
            val allowedRoots = storages.map { it.path }
            val seenPaths = mutableSetOf<String>()

            storages.forEach { storage ->
                if (_uiState.value.results.size >= MAX_RESULTS) return@forEach

                fileRepository.searchFilesStreaming(
                    rootPath = storage.path,
                    query = query,
                    allowedRoots = allowedRoots,
                    filters = filters,
                    maxResults = MAX_RESULTS - _uiState.value.results.size
                ).collect { file ->
                    // Overlapping or duplicate storage roots can stream the same
                    // file path from more than one root. Skip duplicates so the
                    // results list never holds two items with the same path, which
                    // would produce duplicate LazyColumn keys and crash measurement.
                    if (file.path in seenPaths) return@collect
                    seenPaths.add(file.path)
                    _uiState.update { state ->
                        if (state.results.size < MAX_RESULTS) {
                            state.copy(results = state.results + file)
                        } else {
                            state
                        }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchComplete = true
                )
            }
        }
    }

    fun showDeleteDialog(file: FileItem) {
        _uiState.value = _uiState.value.copy(fileToDelete = file)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(fileToDelete = null)
    }

    fun onDeleteConfirmed() {
        val file = _uiState.value.fileToDelete ?: return
        viewModelScope.launch {
            val allPaths = fileRepository.collectAllPaths(listOf(file))
            val success = fileRepository.delete(listOf(file))
            if (success) {
                MediaStoreUtil.notifyDeleted(context, allPaths)
                AnalyticsTracker.trackDeleteCompleted(1, "search")
                _uiState.value = _uiState.value.copy(
                    fileToDelete = null,
                    results = _uiState.value.results.filter { it.path != file.path }
                )
            } else {
                AnalyticsTracker.trackOperationFailed("delete", "unknown")
                _uiState.value = _uiState.value.copy(fileToDelete = null)
                _events.emit(SearchUiEvent.ShowToastRes(R.string.delete_error))
            }
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
            return SearchViewModel(
                application = application,
                fileRepository = FileRepository(),
                storageRepository = StorageRepository(AndroidStorageSource(application)),
                preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
            ) as T
        }
    }

    companion object {
        private val DEBOUNCE_DELAY = 300.milliseconds
        private const val MAX_RESULTS = 100
    }
}
