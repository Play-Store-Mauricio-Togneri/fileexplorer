package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import kotlinx.coroutines.flow.Flow

interface RecentFilesSource {
    val recentFilesFlow: Flow<List<RecentFile>>
    suspend fun getRecentFiles(): List<RecentFile>
    suspend fun updateRecentFiles(transform: (List<RecentFile>) -> List<RecentFile>)
    suspend fun clearRecentFiles()
}
