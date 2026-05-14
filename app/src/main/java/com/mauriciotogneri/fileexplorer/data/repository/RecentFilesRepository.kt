package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import java.io.File

val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_files")

class RecentFilesRepository(private val dataStore: DataStore<Preferences>) {

    suspend fun getRecentFiles(): List<RecentFile> = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()
        val json = preferences[KEY_RECENT_FILES] ?: return@withContext emptyList()
        try {
            val array = JSONArray(json)
            val files = mutableListOf<RecentFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString(JSON_PATH)
                if (File(path).exists()) {
                    files.add(
                        RecentFile(
                            path = path,
                            name = obj.getString(JSON_NAME),
                            mimeType = obj.getString(JSON_MIME_TYPE),
                            lastOpenedTimestamp = obj.getLong(JSON_TIMESTAMP)
                        )
                    )
                }
            }
            files.sortedByDescending { it.lastOpenedTimestamp }
        } catch (e: Exception) {
            ErrorReporter.error(e, "load_recent_files")
            emptyList()
        }
    }

    suspend fun addRecentFile(file: File) = withContext(Dispatchers.IO) {
        if (file.isDirectory) return@withContext

        val currentFiles = getRecentFiles().toMutableList()
        currentFiles.removeAll { it.path == file.absolutePath }

        val newEntry = RecentFile(
            path = file.absolutePath,
            name = file.name,
            mimeType = MimeTypeUtil.getMimeType(file),
            lastOpenedTimestamp = System.currentTimeMillis()
        )
        currentFiles.add(0, newEntry)

        val trimmedList = currentFiles.take(MAX_RECENT_FILES)
        saveRecentFiles(trimmedList)
    }

    suspend fun removeRecentFile(path: String) = withContext(Dispatchers.IO) {
        val currentFiles = getRecentFiles().toMutableList()
        currentFiles.removeAll { it.path == path }
        saveRecentFiles(currentFiles)
    }

    suspend fun clearRecentFiles() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_RECENT_FILES)
        }
    }

    private suspend fun saveRecentFiles(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            val obj = JSONObject().apply {
                put(JSON_PATH, file.path)
                put(JSON_NAME, file.name)
                put(JSON_MIME_TYPE, file.mimeType)
                put(JSON_TIMESTAMP, file.lastOpenedTimestamp)
            }
            array.put(obj)
        }
        dataStore.edit { preferences ->
            preferences[KEY_RECENT_FILES] = array.toString()
        }
    }

    companion object {
        private val KEY_RECENT_FILES = stringPreferencesKey("files")
        private const val MAX_RECENT_FILES = 20

        private const val JSON_PATH = "path"
        private const val JSON_NAME = "name"
        private const val JSON_MIME_TYPE = "mimeType"
        private const val JSON_TIMESTAMP = "timestamp"
    }
}
