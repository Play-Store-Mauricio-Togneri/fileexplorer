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

        val newEntry = RecentFile(
            path = file.absolutePath,
            name = file.name,
            mimeType = MimeTypeUtil.getMimeType(file),
            lastOpenedTimestamp = System.currentTimeMillis()
        )
        source.updateRecentFiles { currentFiles ->
            val deduped = currentFiles.filterNot { it.path == newEntry.path }
            (listOf(newEntry) + deduped).take(MAX_RECENT_FILES)
        }
    }

    suspend fun removeRecentFile(path: String) = withContext(Dispatchers.IO) {
        source.updateRecentFiles { currentFiles ->
            currentFiles.filterNot { it.path == path }
        }
    }

    // Drops entries whose underlying file no longer exists (deleted by this app, another app, or an
    // unmounted volume). recentFilesFlow only re-applies its existence filter when the store is
    // written, so callers must invoke this when the file system may have changed out from under us
    // (e.g. returning to the home screen). The guard avoids a redundant write when nothing is stale.
    suspend fun pruneNonExistentFiles() = withContext(Dispatchers.IO) {
        val currentFiles = source.getRecentFiles()
        if (currentFiles.any { !File(it.path).exists() }) {
            source.updateRecentFiles { files -> files.filter { File(it.path).exists() } }
        }
    }

    suspend fun clearRecentFiles() {
        source.clearRecentFiles()
    }

    companion object {
        private const val MAX_RECENT_FILES = 20
    }
}
