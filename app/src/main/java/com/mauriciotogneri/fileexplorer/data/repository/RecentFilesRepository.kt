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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

val Context.recentFilesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_files",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class RecentFilesRepository(private val source: RecentFilesSource) {

    // distinctBy guards the home-screen LazyRow, which keys by path: updatePath (rename) can leave
    // two stored entries sharing a path, and duplicate keys crash Compose. Also heals stores already
    // corrupted by that bug. Keeps the first of any colliding pair.
    val recentFilesFlow: Flow<List<RecentFile>> = source.recentFilesFlow.map { files ->
        files.existingWithTimestamps().distinctBy { it.path }
    }.flowOn(Dispatchers.IO)

    suspend fun getRecentFiles(): List<RecentFile> = withContext(Dispatchers.IO) {
        source.getRecentFiles().existingWithTimestamps().distinctBy { it.path }
    }

    // Drops entries whose file is gone and stamps each survivor with the file's modification time,
    // which RecentFilesSection needs to key its thumbnail in the memory cache the same way the
    // folder list does. Two stats per entry, so both call paths keep them off the main thread
    // themselves — recentFilesFlow through its own flowOn, getRecentFiles() through withContext —
    // rather than relying on every collector to add a flowOn of its own (they all do today, but the
    // next one to collect recentFilesFlow directly would stat per entry on the main thread and never
    // know why it stalled). exists() is kept as a separate call rather than folded into
    // lastModified() == 0L: that reads as "missing" for a file genuinely stamped at the epoch, which
    // this app can produce when extracting archives, and would silently drop it from recents.
    private fun List<RecentFile>.existingWithTimestamps(): List<RecentFile> = mapNotNull { recent ->
        val file = File(recent.path)
        if (file.exists()) recent.copy(lastModified = file.lastModified()) else null
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

    // Rewrites stored paths after a rename so recents survive instead of being dropped as
    // non-existent (their old path no longer exists on disk). Updates the renamed file itself
    // (path + display name) and any entry living under a renamed directory (prefix rewrite, name
    // unchanged). The File.separator on the prefix stops renaming "/Docs" from also matching a
    // sibling "/DocsBackup". The pre-read guard skips the write (and the recomposition it triggers)
    // when nothing is affected, matching pruneNonExistentFiles.
    suspend fun updatePath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val descendantPrefix = oldPath + File.separator
        val currentFiles = source.getRecentFiles()
        if (currentFiles.none { it.path == oldPath || it.path.startsWith(descendantPrefix) }) {
            return@withContext
        }
        source.updateRecentFiles { files ->
            // distinctBy: a rewritten path can collide with an entry already at newPath (a stale
            // recents entry, or a descendant whose new prefix matches a sibling). Collapse it so the
            // store — and the path-keyed home LazyRow — never holds two entries sharing a path.
            files.map { recentFile ->
                when {
                    recentFile.path == oldPath ->
                        recentFile.copy(
                            path = newPath,
                            name = File(newPath).name,
                            // The rename dialog lets the user change the extension, so refresh the
                            // type from the new name. Recents are always files (never directories).
                            mimeType = MimeTypeUtil.getMimeType(File(newPath))
                        )

                    recentFile.path.startsWith(descendantPrefix) ->
                        recentFile.copy(path = newPath + recentFile.path.substring(oldPath.length))

                    else -> recentFile
                }
            }.distinctBy { it.path }
        }
    }

    // Drops entries whose underlying file no longer exists (deleted by this app, another app, or an
    // unmounted volume) and collapses entries sharing a path. recentFilesFlow only re-applies its
    // existence filter when the store is written, so callers must invoke this when the file system
    // may have changed out from under us (e.g. returning to the home screen). Reads already hide
    // duplicates left by the pre-fix updatePath, but only a write heals the store — until then they
    // consume MAX_RECENT_FILES slots. Both cleanups only remove entries, so a size drop is an exact
    // "needs cleaning" test and avoids a redundant write when the store is already clean. The
    // transform recomputes the cleanup instead of writing cleanedFiles: it must run on the list
    // DataStore holds at write time, or a concurrent addRecentFile would be lost.
    suspend fun pruneNonExistentFiles() = withContext(Dispatchers.IO) {
        val currentFiles = source.getRecentFiles()
        val cleanedFiles = currentFiles.filter { File(it.path).exists() }.distinctBy { it.path }
        if (cleanedFiles.size != currentFiles.size) {
            source.updateRecentFiles { files ->
                files.filter { File(it.path).exists() }.distinctBy { it.path }
            }
        }
    }

    suspend fun clearRecentFiles() {
        source.clearRecentFiles()
    }

    companion object {
        private const val MAX_RECENT_FILES = 20
    }
}
