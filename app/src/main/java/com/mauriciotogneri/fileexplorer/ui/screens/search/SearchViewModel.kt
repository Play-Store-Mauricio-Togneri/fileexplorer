package com.mauriciotogneri.fileexplorer.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchUiEvent {
    data class ShowToastRes(val messageResId: Int) : SearchUiEvent()
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchUiEvent>()
    val events: SharedFlow<SearchUiEvent> = _events.asSharedFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var currentUncompressTarget: String = ""

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { currentUncompressTarget }
    )

    init {
        queryFlow
            .debounce(DEBOUNCE_MS)
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
                        // Search results don't need refresh - the extracted files
                        // are in a different location
                    }
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(
            query = query,
            searchComplete = false
        )
        queryFlow.value = query
    }

    fun clearQuery() {
        onQueryChange("")
        _uiState.value = SearchUiState()
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = SearchUiState(query = query)
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                searchComplete = false,
                results = emptyList()
            )

            val storages = storageRepository.getStorages()
            val results = mutableListOf<FileItem>()

            storages.forEach { storage ->
                if (results.size >= MAX_RESULTS) return@forEach

                fileRepository.searchFilesStreaming(
                    rootPath = storage.path,
                    query = query,
                    maxResults = MAX_RESULTS - results.size
                ).collect { file ->
                    if (results.size < MAX_RESULTS) {
                        results.add(file)
                        _uiState.value = _uiState.value.copy(
                            results = results.toList()
                        )
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchComplete = true
            )
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
            val allPaths = withContext(Dispatchers.IO) {
                fileRepository.collectAllPaths(listOf(file))
            }
            val success = fileRepository.delete(listOf(file))
            if (success) {
                MediaStoreUtil.notifyDeleted(context, allPaths)
                _uiState.value = _uiState.value.copy(
                    fileToDelete = null,
                    results = _uiState.value.results.filter { it.path != file.path }
                )
            } else {
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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(
                context = context.applicationContext,
                fileRepository = FileRepository(),
                storageRepository = StorageRepository(context)
            ) as T
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MAX_RESULTS = 100
    }
}
