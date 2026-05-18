package com.mauriciotogneri.fileexplorer.ui.screens.search

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchComplete: Boolean = false,
    val fileToDelete: FileItem? = null,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null
) {
    val showNoResults: Boolean
        get() = query.isNotEmpty() && searchComplete && results.isEmpty()
}
