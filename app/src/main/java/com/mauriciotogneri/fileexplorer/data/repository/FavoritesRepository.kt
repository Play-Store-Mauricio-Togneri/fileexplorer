package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.source.FavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
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

    // Rewrites stored paths after a rename so favorites survive instead of being dropped as
    // non-existent (their old path no longer exists on disk). Updates the renamed item itself
    // (path + display name) and any entries living under a renamed directory (prefix rewrite, name
    // unchanged). The File.separator on the prefix stops renaming "/Docs" from also matching a
    // sibling "/DocsBackup". The pre-read guard skips the write (and the recomposition it triggers)
    // when nothing is affected, matching pruneNonExistentFiles.
    suspend fun updatePath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val descendantPrefix = oldPath + File.separator
        val currentFiles = source.getFavorites()
        if (currentFiles.none { it.path == oldPath || it.path.startsWith(descendantPrefix) }) {
            return@withContext
        }
        source.updateFavorites { files ->
            files.map { favorite ->
                when {
                    favorite.path == oldPath ->
                        favorite.copy(
                            path = newPath,
                            name = File(newPath).name,
                            // The rename dialog lets the user change the extension, so refresh the
                            // type from the new name. Directories carry an empty mimeType by
                            // convention (getMimeType would return "*/*"), so leave theirs untouched.
                            mimeType = if (favorite.isDirectory) favorite.mimeType else MimeTypeUtil.getMimeType(File(newPath))
                        )

                    favorite.path.startsWith(descendantPrefix) ->
                        favorite.copy(path = newPath + favorite.path.substring(oldPath.length))

                    else -> favorite
                }
            }
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
