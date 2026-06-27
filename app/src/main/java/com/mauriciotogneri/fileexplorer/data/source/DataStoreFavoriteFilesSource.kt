package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DataStoreFavoriteFilesSource(
    private val dataStore: DataStore<Preferences>
) : FavoriteFilesSource {

    override val favoritesFlow: Flow<List<Favorite>> = dataStore.data.map { preferences ->
        parseFavorites(preferences[KEY_FAVORITE_FILES])
    }.flowOn(Dispatchers.IO)

    override suspend fun getFavorites(): List<Favorite> = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()
        parseFavorites(preferences[KEY_FAVORITE_FILES])
    }

    override suspend fun updateFavorites(transform: (List<Favorite>) -> List<Favorite>) {
        dataStore.edit { preferences ->
            val current = parseFavorites(preferences[KEY_FAVORITE_FILES])
            preferences[KEY_FAVORITE_FILES] = serializeFavorites(transform(current))
        }
    }

    private fun serializeFavorites(files: List<Favorite>): String {
        val array = JSONArray()
        files.forEach { file ->
            val obj = JSONObject().apply {
                put(JSON_PATH, file.path)
                put(JSON_NAME, file.name)
                put(JSON_IS_DIRECTORY, file.isDirectory)
                put(JSON_MIME_TYPE, file.mimeType)
                put(JSON_TIMESTAMP, file.favoritedTimestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }

    override suspend fun clearFavorites() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_FAVORITE_FILES)
        }
    }

    private fun parseFavorites(json: String?): List<Favorite> {
        if (json == null) return emptyList()
        return try {
            val array = JSONArray(json)
            val files = mutableListOf<Favorite>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                files.add(
                    Favorite(
                        path = obj.getString(JSON_PATH),
                        name = obj.getString(JSON_NAME),
                        isDirectory = obj.optBoolean(JSON_IS_DIRECTORY, false),
                        mimeType = obj.getString(JSON_MIME_TYPE),
                        favoritedTimestamp = obj.getLong(JSON_TIMESTAMP)
                    )
                )
            }
            files.sortedByDescending { it.favoritedTimestamp }
        } catch (e: Exception) {
            ErrorReporter.error(e, "load_favorite_files")
            emptyList()
        }
    }

    companion object {
        private val KEY_FAVORITE_FILES = stringPreferencesKey("files")

        private const val JSON_PATH = "path"
        private const val JSON_NAME = "name"
        private const val JSON_IS_DIRECTORY = "isDirectory"
        private const val JSON_MIME_TYPE = "mimeType"
        private const val JSON_TIMESTAMP = "timestamp"
    }
}
