package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DataStoreRecentFilesSource(
    private val dataStore: DataStore<Preferences>
) : RecentFilesSource {

    override val recentFilesFlow: Flow<List<RecentFile>> = dataStore.data.map { preferences ->
        parseRecentFiles(preferences[KEY_RECENT_FILES])
    }.flowOn(Dispatchers.IO)

    override suspend fun getRecentFiles(): List<RecentFile> = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()
        parseRecentFiles(preferences[KEY_RECENT_FILES])
    }

    override suspend fun updateRecentFiles(transform: (List<RecentFile>) -> List<RecentFile>) {
        dataStore.edit { preferences ->
            val current = parseRecentFiles(preferences[KEY_RECENT_FILES])
            preferences[KEY_RECENT_FILES] = serializeRecentFiles(transform(current))
        }
    }

    private fun serializeRecentFiles(files: List<RecentFile>): String {
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
        return array.toString()
    }

    override suspend fun clearRecentFiles() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_RECENT_FILES)
        }
    }

    private fun parseRecentFiles(json: String?): List<RecentFile> {
        if (json == null) return emptyList()
        return try {
            val array = JSONArray(json)
            val files = mutableListOf<RecentFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                files.add(
                    RecentFile(
                        path = obj.getString(JSON_PATH),
                        name = obj.getString(JSON_NAME),
                        mimeType = obj.getString(JSON_MIME_TYPE),
                        lastOpenedTimestamp = obj.getLong(JSON_TIMESTAMP)
                    )
                )
            }
            files.sortedByDescending { it.lastOpenedTimestamp }
        } catch (e: Exception) {
            ErrorReporter.error(e, "load_recent_files")
            emptyList()
        }
    }

    companion object {
        private val KEY_RECENT_FILES = stringPreferencesKey("files")

        private const val JSON_PATH = "path"
        private const val JSON_NAME = "name"
        private const val JSON_MIME_TYPE = "mimeType"
        private const val JSON_TIMESTAMP = "timestamp"
    }
}
