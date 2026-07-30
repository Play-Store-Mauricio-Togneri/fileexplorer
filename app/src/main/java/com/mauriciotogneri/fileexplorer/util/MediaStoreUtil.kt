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
     * Tells MediaStore that [paths] are gone, so galleries and other media views stop offering
     * them before the next full scan.
     *
     * Not every media provider accepts a delete on the Files collection: some reject the URI
     * outright with an `Unknown URL` failure, and a provider can equally refuse rows this app is
     * not allowed to touch. The rejection is for the collection rather than for one path, so the
     * first failure ends the loop and hands every path to the media scanner instead — scanning a
     * path that no longer exists drops its row too, through an API every device supports.
     *
     * Nothing is allowed out either way. This runs after the files have already been deleted, and
     * every caller has follow-up work that matters more than the cleanup: rescanning the new name
     * of a renamed file, dropping the item from favourites and recents, closing the dialog it was
     * deleted from. A cleanup failure that escaped would skip all of it and report an operation
     * that did succeed as a failure.
     */
    suspend fun notifyDeleted(context: Context, paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val uri = MediaStore.Files.getContentUri("external")
        try {
            paths.forEach { path ->
                context.contentResolver.delete(
                    uri,
                    "${MediaStore.Files.FileColumns.DATA}=?",
                    arrayOf(path)
                )
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
}
