package com.mauriciotogneri.fileexplorer.ui.screens.search

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchComplete: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    val folderSecondLine: FolderSecondLine = FolderSecondLine.ITEM_COUNT,
    val fileSecondLine: FileSecondLine = FileSecondLine.SIZE,
    val favoritePaths: Set<String> = emptySet(),
    val fileToDelete: FileItem? = null,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null,
    /**
     * Extractions running in this screen's ViewModel. Leaving the screen clears the ViewModel and
     * stops each of them part-way, so while any runs a way out first shows the progress dialog still
     * held back by its delay: see [SearchViewModel.showPendingProgress].
     */
    val runningExtractions: Int = 0,
    val pendingApkInstall: FileItem? = null
) {
    val showNoResults: Boolean
        get() = query.isNotEmpty() && searchComplete && results.isEmpty()
}
