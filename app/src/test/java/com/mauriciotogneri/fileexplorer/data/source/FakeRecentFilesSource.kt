package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeRecentFilesSource(
    initialFiles: List<RecentFile> = emptyList()
) : RecentFilesSource {

    private val _files = MutableStateFlow(initialFiles)

    var updateCount = 0
        private set

    override val recentFilesFlow: Flow<List<RecentFile>> = _files

    override suspend fun getRecentFiles(): List<RecentFile> = _files.value

    override suspend fun updateRecentFiles(transform: (List<RecentFile>) -> List<RecentFile>) {
        updateCount++
        _files.update { transform(it) }
    }

    override suspend fun clearRecentFiles() {
        _files.value = emptyList()
    }
}
