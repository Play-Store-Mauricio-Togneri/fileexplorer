package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRecentFilesSource(
    initialFiles: List<RecentFile> = emptyList()
) : RecentFilesSource {

    private val _files = MutableStateFlow(initialFiles)

    override val recentFilesFlow: Flow<List<RecentFile>> = _files

    override suspend fun getRecentFiles(): List<RecentFile> = _files.value

    override suspend fun saveRecentFiles(files: List<RecentFile>) {
        _files.value = files
    }

    override suspend fun clearRecentFiles() {
        _files.value = emptyList()
    }
}
