package com.mauriciotogneri.fileexplorer.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SortManager {
    private val _sortMode = MutableStateFlow(SortMode.NAME_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    val currentSortMode: SortMode
        get() = _sortMode.value

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }
}
