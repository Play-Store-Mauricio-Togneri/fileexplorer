package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.source.RecentFilesSource
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_files",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class RecentFilesRepository(private val source: RecentFilesSource) {

    val recentFilesFlow: Flow<List<RecentFile>> = source.recentFilesFlow.map { files ->
        files.filter { File(it.path).exists() }
    }

    suspend fun getRecentFiles(): List<RecentFile> = withContext(Dispatchers.IO) {
        source.getRecentFiles().filter { File(it.path).exists() }
    }

    suspend fun addRecentFile(file: File) = withContext(Dispatchers.IO) {
        if (file.isDirectory) return@withContext

        val currentFiles = source.getRecentFiles().toMutableList()
        currentFiles.removeAll { it.path == file.absolutePath }

        val newEntry = RecentFile(
            path = file.absolutePath,
            name = file.name,
            mimeType = MimeTypeUtil.getMimeType(file),
            lastOpenedTimestamp = System.currentTimeMillis()
        )
        currentFiles.add(0, newEntry)

        val trimmedList = currentFiles.take(MAX_RECENT_FILES)
        source.saveRecentFiles(trimmedList)
    }

    suspend fun removeRecentFile(path: String) = withContext(Dispatchers.IO) {
        val currentFiles = source.getRecentFiles().toMutableList()
        currentFiles.removeAll { it.path == path }
        source.saveRecentFiles(currentFiles)
    }

    suspend fun clearRecentFiles() {
        source.clearRecentFiles()
    }

    companion object {
        private const val MAX_RECENT_FILES = 20
    }
}
