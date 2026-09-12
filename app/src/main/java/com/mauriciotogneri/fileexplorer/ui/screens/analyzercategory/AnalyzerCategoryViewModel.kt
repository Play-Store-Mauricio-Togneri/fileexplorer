package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class AnalyzerCategoryUiState(
    /** The category's share of the volume, as its chart row stated it. */
    val totalBytes: Long = 0L,
    /** The pages loaded so far, biggest file first. */
    val files: List<FileItem> = emptyList(),
    val isLoadingPage: Boolean = false,
    val hasMore: Boolean = true
) {
    /**
     * Only once the first page has come back and brought nothing. Until then the list is empty
     * because it has not been read yet, which is a different thing to show.
     */
    val isEmpty: Boolean get() = files.isEmpty() && !hasMore
}

/**
 * Lists one analyzer category's files, a page at a time.
 *
 * The scan retained a path and a size per file and nothing else, so everything a row draws — the
 * name, the MIME type behind its icon, the timestamps — has to be read back from disk. That read
 * is why the list is paged at all: [FileItem.from] costs a `readAttributes` per file, so a category
 * of ten thousand files would spend ten thousand syscalls before showing a single row. A page pays
 * a hundred.
 *
 * The size a row displays is the one the scan measured, not the one the read-back reports. They are
 * the same figure for any file nobody touched in between, and using the scanned one keeps a row's
 * label agreeing with the position it was sorted into.
 */
class AnalyzerCategoryViewModel(
    private val categoryFiles: CategoryFiles,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AnalyzerCategoryUiState(totalBytes = categoryFiles.totalBytes)
    )
    val uiState: StateFlow<AnalyzerCategoryUiState> = _uiState.asStateFlow()

    init {
        loadNextPage()
    }

    /**
     * Appends the next page, or does nothing if one is already in flight or the end has been
     * reached. The screen asks on every composition of its trailing item, and a list that is being
     * scrolled hard composes it more than once.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingPage || !state.hasMore) return

        _uiState.update { it.copy(isLoadingPage = true) }

        viewModelScope.launch {
            val from = state.files.size
            val to = minOf(from + PAGE_SIZE, categoryFiles.entries.size)

            val page = withContext(ioDispatcher) {
                categoryFiles.entries.subList(from, to).map { entry ->
                    FileItem.from(File(entry.path)).copy(size = entry.size)
                }
            }

            _uiState.update {
                it.copy(
                    files = it.files + page,
                    isLoadingPage = false,
                    hasMore = to < categoryFiles.entries.size
                )
            }
        }
    }

    class Factory(private val categoryFiles: CategoryFiles) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyzerCategoryViewModel(categoryFiles) as T
        }
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}
