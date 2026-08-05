package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreUtil {

    fun scanFile(context: Context, path: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            null,
            null
        )
    }

    fun scanFiles(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(
            context,
            paths.toTypedArray(),
            null,
            null
        )
    }

    /**
     * Tells MediaStore that exactly [paths] are gone, so galleries and other media views stop
     * offering them before the next full scan. Anything that was below a directory in [paths]
     * keeps its row — use [notifyTreeDeleted] for a directory whose contents went with it.
     */
    suspend fun notifyDeleted(context: Context, paths: List<String>) = withContext(Dispatchers.IO) {
        removeRows(context, paths, includeDescendants = false)
    }

    /**
     * Tells MediaStore that [paths] and everything that was under them are gone.
     *
     * Each path is matched exactly and as a prefix in a single delete, rather than the caller
     * expanding a deleted tree into one path per descendant: enumerating a large tree costs a full
     * walk whose result has to stay in memory until the whole operation finishes, which is
     * unbounded in the size of the tree and has run devices out of heap.
     *
     * Only for paths whose deletion fully succeeded. The prefix clause drops the rows of every
     * descendant, and a media provider unlinks the file backing a row it removes, so reporting a
     * tree that is still partly on disk would delete what the operation left behind.
     */
    suspend fun notifyTreeDeleted(context: Context, paths: List<String>) = withContext(Dispatchers.IO) {
        removeRows(context, paths, includeDescendants = true)
    }

    /**
     * Not every media provider accepts a delete on the Files collection: some reject the URI
     * outright with an `Unknown URL` failure, and a provider can equally refuse rows this app is
     * not allowed to touch. The rejection is for the collection rather than for one path, so the
     * first failure ends the loop and hands every path to the media scanner instead — scanning a
     * path that no longer exists drops its row too, through an API every device supports. The
     * scanner only sees the paths it was given, so rows below a deleted directory survive until the
     * next full scan on providers that take this fallback.
     *
     * Nothing is allowed out either way. This runs after the files have already been deleted, and
     * every caller has follow-up work that matters more than the cleanup: rescanning the new name
     * of a renamed file, dropping the item from favourites and recents, closing the dialog it was
     * deleted from. A cleanup failure that escaped would skip all of it and report an operation
     * that did succeed as a failure.
     */
    private fun removeRows(context: Context, paths: List<String>, includeDescendants: Boolean) {
        if (paths.isEmpty()) return
        val uri = MediaStore.Files.getContentUri("external")
        val data = MediaStore.Files.FileColumns.DATA
        try {
            paths.forEach { path ->
                if (includeDescendants) {
                    // GLOB rather than LIKE: LIKE is case-insensitive for ASCII unless the database
                    // opts out, so on a case-sensitive volume its prefix would also match a sibling
                    // directory differing only in case — whose files are still on disk for the
                    // provider to unlink. GLOB compares as bytes, and unlike a case-insensitive
                    // LIKE it can still use the index on the path column.
                    context.contentResolver.delete(
                        uri,
                        "$data=? OR $data GLOB ?",
                        arrayOf(path, "${path.escapeForGlob()}/*")
                    )
                } else {
                    context.contentResolver.delete(uri, "$data=?", arrayOf(path))
                }
            }
        } catch (e: Exception) {
            // The recovery is guarded too: reporting the failure and scanning around it are both
            // part of the cleanup, so neither may become the escape this catch exists to prevent.
            // A guard each, rather than one around both: ErrorReporter.report calls
            // FirebaseCrashlytics.getInstance() unguarded and throws when Firebase never
            // initialised, which under a shared guard would skip the scan — the actual recovery.
            runCatching { scanFiles(context, paths) }
            runCatching { ErrorReporter.warning(e, "notify_media_store_deleted") }
        }
    }

    /**
     * Escapes the wildcards SQLite's `GLOB` recognises so a path is matched literally. `?` matches
     * any single character and `[` opens a character class, both of which occur in file names, so
     * an unescaped prefix would match paths the caller never reported. GLOB has no escape
     * character; a wildcard is quoted by wrapping it in a single-character class instead.
     */
    private fun String.escapeForGlob(): String = buildString {
        this@escapeForGlob.forEach { character ->
            when (character) {
                '*', '?', '[' -> append("[").append(character).append("]")
                else -> append(character)
            }
        }
    }
}
