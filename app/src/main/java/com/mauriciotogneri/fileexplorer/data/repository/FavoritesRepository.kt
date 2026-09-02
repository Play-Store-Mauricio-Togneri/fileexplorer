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
import com.mauriciotogneri.fileexplorer.data.util.isForgettable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

val Context.favoriteFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_files",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class FavoritesRepository(private val source: FavoriteFilesSource) {

    // distinctBy guards the home-screen LazyRow, which keys by path: updatePath (rename) can leave
    // two stored entries sharing a path, and duplicate keys crash Compose. Also heals stores already
    // corrupted by that bug. Keeps the first of any colliding pair.
    val favoritesFlow: Flow<List<Favorite>> = source.favoritesFlow.map { favorites ->
        favorites.existingWithTimestamps().distinctBy { it.path }
    }.flowOn(Dispatchers.IO)

    suspend fun getFavorites(): List<Favorite> = withContext(Dispatchers.IO) {
        source.getFavorites().existingWithTimestamps().distinctBy { it.path }
    }

    // Drops entries whose file is gone and stamps each survivor with the file's modification time,
    // which FavoritesSection needs to key its thumbnail in the memory cache the same way the folder
    // list does. Two stats per entry, so both call paths keep them off the main thread themselves —
    // favoritesFlow through its own flowOn, getFavorites() through withContext — rather than relying
    // on every collector to add a flowOn of its own (they all do today, but the next one to collect
    // favoritesFlow directly would stat per entry on the main thread and never know why it stalled).
    // exists() is kept as a separate call rather than folded into lastModified() == 0L: that reads as
    // "missing" for a file genuinely stamped at the epoch, which this app can produce when extracting
    // archives, and would silently drop it from favorites.
    private fun List<Favorite>.existingWithTimestamps(): List<Favorite> = mapNotNull { favorite ->
        val file = File(favorite.path)
        if (file.exists()) favorite.copy(lastModified = file.lastModified()) else null
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
            // distinctBy: a rewritten path can collide with an entry already at newPath (a stale
            // favorite, or a descendant whose new prefix matches a sibling). Collapse it so the
            // store — and the path-keyed home LazyRow — never holds two entries sharing a path.
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
            }.distinctBy { it.path }
        }
    }

    // Drops entries whose underlying file no longer exists and collapses entries sharing a path.
    // favoritesFlow only re-applies its existence filter when the store is written, so callers must
    // invoke this when the file system may have changed (e.g. returning to the home screen). Reads
    // already hide duplicates left by the pre-fix updatePath, but only a write heals the store. Both
    // cleanups only remove entries, so a size drop is an exact "needs cleaning" test and avoids a
    // redundant write when the store is already clean. The transform recomputes the cleanup instead
    // of writing cleanedFiles: it must run on the list DataStore holds at write time, or a
    // concurrent addFavorite would be lost.
    //
    // [mountedRoots] is what keeps "the file is gone" apart from "the volume is gone" — see
    // [isForgettable]. An entry on a volume that is not mounted is kept, so ejecting an SD card
    // does not silently empty the user's favorites of everything that lives on it.
    suspend fun pruneNonExistentFiles(mountedRoots: List<String>) = withContext(Dispatchers.IO) {
        val currentFiles = source.getFavorites()
        val cleanedFiles = currentFiles.filterNot { isForgettable(it.path, mountedRoots) }.distinctBy { it.path }
        if (cleanedFiles.size != currentFiles.size) {
            source.updateFavorites { files ->
                files.filterNot { isForgettable(it.path, mountedRoots) }.distinctBy { it.path }
            }
        }
    }

    suspend fun clearFavorites() {
        source.clearFavorites()
    }
}
