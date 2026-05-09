package com.mauriciotogneri.fileexplorer.ui.screens.search

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.FileItem

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchComplete: Boolean = false
) {
    val showNoResults: Boolean
        get() = query.isNotEmpty() && searchComplete && results.isEmpty()
}
