package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.source.FavoriteFilesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

val Context.favoriteFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_files",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class FavoritesRepository(private val source: FavoriteFilesSource) {

    val favoritesFlow: Flow<List<Favorite>> = source.favoritesFlow.map { favorites ->
        favorites.filter { File(it.path).exists() }
    }

    suspend fun getFavorites(): List<Favorite> = withContext(Dispatchers.IO) {
        source.getFavorites().filter { File(it.path).exists() }
    }

    // Favorites intentionally have no size cap (unlike recents): they are deliberate user choices.
    // A granular signature lets both FileItem callers (folder view) and RecentFile callers (recents
    // sheet) add an entry without constructing a FileItem or touching disk.
    suspend fun addFavorite(
        path: String,
        name: String,
        isDirectory: Boolean,
        mimeType: String
    ) = withContext(Dispatchers.IO) {
        val newEntry = Favorite(
            path = path,
            name = name,
            isDirectory = isDirectory,
            mimeType = mimeType,
            favoritedTimestamp = System.currentTimeMillis()
        )
        source.updateFavorites { currentFiles ->
            val deduped = currentFiles.filterNot { it.path == newEntry.path }
            listOf(newEntry) + deduped
        }
    }

    suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        source.updateFavorites { currentFiles ->
            currentFiles.filterNot { it.path == path }
        }
    }

    // Drops entries whose underlying file no longer exists. favoritesFlow only re-applies its
    // existence filter when the store is written, so callers must invoke this when the file system
    // may have changed (e.g. returning to the home screen). The guard avoids a redundant write when
    // nothing is stale.
    suspend fun pruneNonExistentFiles() = withContext(Dispatchers.IO) {
        val currentFiles = source.getFavorites()
        if (currentFiles.any { !File(it.path).exists() }) {
            source.updateFavorites { files -> files.filter { File(it.path).exists() } }
        }
    }

    suspend fun clearFavorites() {
        source.clearFavorites()
    }
}
