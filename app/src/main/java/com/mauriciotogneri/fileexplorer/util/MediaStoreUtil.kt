package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreUtil {

    fun scanFile(context: Context, path: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            null,
            null
        )
    }

    /**
     * Registers [paths] with the media scanner so galleries and other media views pick them up
     * before the next full scan.
     *
     * Nothing is allowed out, for the same reason as [removeRows]: this is cleanup running
     * alongside work that matters more, and `MediaScannerConnection.scanFile` binds a service to do
     * it — a bind that a device with a missing or disabled media provider can refuse. An extraction
     * scans each batch of files while the flow is still producing, so a failure that escaped here
     * would cancel it and roll back everything extracted so far.
     */
    fun scanFiles(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                paths.toTypedArray(),
                null,
                null
            )
        }
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
     * Each path is matched exactly and as a prefix by one selection, rather than the caller
     * expanding a deleted tree into one path per descendant: enumerating a large tree costs a full
     * walk whose result has to stay in memory until the whole operation finishes, which is
     * unbounded in the size of the tree and has run devices out of heap.
     *
     * Only for paths whose deletion fully succeeded. The prefix selects the rows of every
     * descendant, and a media provider unlinks the file backing a row it removes, so reporting a
     * tree that is still partly on disk would delete what the operation left behind.
     */
    suspend fun notifyTreeDeleted(context: Context, paths: List<String>) = withContext(Dispatchers.IO) {
        removeRows(context, paths, includeDescendants = true)
    }

    /**
     * Not every media provider accepts a delete on the Files collection: some reject the URI
     * outright with an `Unknown URL` failure, and a provider can equally refuse rows this app is
     * not allowed to touch. The first failure ends the loop and hands every path to the media
     * scanner instead — scanning a path that no longer exists drops its row too, through an API
     * every device supports. Ending the loop rather than carrying on per path covers both shapes
     * the failure comes in: a rejection of the whole collection, where the remaining paths would
     * be refused the same way, and rows a provider keeps under one path, where the scanner is the
     * recovery either way and it is handed all of them.
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
                    removeTreeRows(context, uri, data, path)
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
            runCatching { scanFiles(context, scanTargetsFor(paths, includeDescendants)) }
            runCatching { ErrorReporter.warning(e, "notify_media_store_deleted") }
        }
    }

    /**
     * Drops the row of [path] and of every row that was under it, a bounded number of rows per
     * delete.
     *
     * One delete for the whole prefix is the obvious form and it fails on large trees: the media
     * provider queries every matching row and removes each row from inside that cursor's own
     * iteration, so once the result outgrows a CursorWindow the refill re-queries a table that has
     * meanwhile shrunk and the row it asks for is no longer there — `Couldn't read row N, col 0
     * from CursorWindow`, thrown in the provider process about 3,600 rows in. Deleting by id in
     * chunks keeps every cursor the provider opens for us inside its first window.
     *
     * The ids are re-queried each round rather than paged with an offset: the previous round's rows
     * are gone, so an offset would step over rows that still remain. Only one chunk of ids is ever
     * held, which keeps this bounded in memory for a tree of any size.
     */
    private fun removeTreeRows(context: Context, uri: Uri, data: String, path: String) {
        // GLOB rather than LIKE: LIKE is case-insensitive for ASCII unless the database opts out,
        // so on a case-sensitive volume its prefix would also match a sibling directory differing
        // only in case — whose files are still on disk for the provider to unlink. GLOB compares as
        // bytes, and unlike a case-insensitive LIKE it can still use the index on the path column.
        val selection = "$data=? OR $data GLOB ?"
        val selectionArgs = arrayOf(path, "${path.escapeForGlob()}/*")
        var previousIds: Set<Long>? = null
        while (true) {
            val ids = idsMatching(context, uri, selection, selectionArgs)
            if (ids.isEmpty()) return

            // Progress is read from what the provider still matches, not from the count the delete
            // returns, because that count answers a different question in both directions. A row
            // dropped by a media scan between the query and the delete — this app starts scans of
            // its own — makes a delete that had nothing left to do return zero, and the framework
            // reports a provider that died mid-call the same way; neither is a tree left behind.
            // A round that comes back with the ids of the round before it is: nothing was removed,
            // so the rows are stuck, and only the scanner — which runs as the provider itself, so
            // it is not bound by whatever declined us — can still reconcile them. That joins the
            // failure path which already hands every path to it, and bounds the loop without
            // trusting the provider, since a round either shrinks what it matches or ends here.
            // Counts only in the message: it is reported, and a report never identifies a file.
            val currentIds = ids.toSet()
            if (currentIds == previousIds) {
                error("MediaStore kept ${ids.size} rows matched under a deleted tree")
            }
            previousIds = currentIds

            // The ids are inlined rather than bound: they come back from the provider as numbers,
            // and a bound parameter each would put a chunk against SQLite's limit on how many a
            // statement may carry.
            context.contentResolver.delete(
                uri,
                "${MediaStore.Files.FileColumns._ID} IN (${ids.joinToString(",")})",
                null
            )
        }
    }

    /**
     * At most [MAX_ROWS_PER_DELETE] of the ids matching [selection], taken as the first rows of the
     * cursor rather than with a `LIMIT`: the supported way to limit a query is a query-args bundle
     * that providers only honour from R, and a `LIMIT` smuggled into the sort order is rejected by
     * the strict SQL grammar newer providers apply. A cursor is filled a window at a time, so
     * reading a few hundred rows of a single numeric column stays inside the first window whatever
     * the query matched — the count the provider computes to describe the cursor still walks every
     * matching row, which is what [MAX_ROWS_PER_DELETE] trades against.
     *
     * No cursor at all is a failure rather than an empty tree, and is raised as one. It is how a
     * query reports the provider it could not reach, where a delete on the same collection throws
     * `Unknown URL` — so reading it as "nothing left to delete" would turn exactly the broken
     * provider this file's fallback was written for into a silent no-op, leaving every row behind
     * with nothing reported.
     */
    private fun idsMatching(
        context: Context,
        uri: Uri,
        selection: String,
        selectionArgs: Array<String>
    ): List<Long> {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            selection,
            selectionArgs,
            null
        ) ?: error("MediaStore returned no cursor for a deleted tree")

        return cursor.use {
            val ids = ArrayList<Long>(MAX_ROWS_PER_DELETE)
            while (ids.size < MAX_ROWS_PER_DELETE && it.moveToNext()) {
                ids.add(it.getLong(0))
            }
            ids
        }
    }

    /**
     * What the scanner is handed once the provider has refused the delete.
     *
     * A path that no longer exists only ever drops its own row, so for a deleted tree the roots
     * alone would leave every row beneath them behind until the next full scan. Their parents are
     * still on disk, and a scanned directory is walked, so the scanner reaches the whole missing
     * subtree without this having to enumerate it — which is what ran devices out of heap and is
     * the reason [removeRows] stopped expanding trees in the first place.
     *
     * The roots are kept alongside the parents to cover a root whose parent went too, and the
     * result is deduplicated because a multi-selection is usually siblings sharing one parent.
     */
    private fun scanTargetsFor(paths: List<String>, includeDescendants: Boolean): List<String> {
        if (!includeDescendants) {
            return paths
        }

        return (paths + paths.mapNotNull { File(it).parent }).distinct()
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

    /**
     * How many ids one round collects, and so how many rows its delete may match. A CursorWindow
     * held around 3,600 of the provider's own rows for the paths that reported the failure, and a
     * row is mostly its path, so a volume with longer ones fits proportionally fewer — the margin
     * here is for those.
     */
    private const val MAX_ROWS_PER_DELETE = 500
}
