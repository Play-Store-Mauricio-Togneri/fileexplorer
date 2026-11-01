package com.mauriciotogneri.fileexplorer.data.source

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.mtp.MtpConstants
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.isSymlink
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * [MediaIndexSync] for this device.
 *
 * From Android 11 (API 30) the system's media service updates the index for every file operation
 * the app makes through a file path, so there is nothing to do; [start] only clears a queue saved
 * before an OS upgrade.
 *
 * Up to Android 10 every operation queues one entry — the roots it created and removed — and one
 * worker syncs the entries one at a time, in order, reading the disk when an entry runs rather than
 * when it was queued:
 * - Created roots are walked, links skipped as every walk in the app does, and each file found is
 *   scanned.
 * - Removed roots are looked up in the index a page at a time, and only the rows whose file no
 *   longer exists are scanned, which drops them — except folders' rows on Android 10, whose scanner
 *   would drop their siblings' rows too. A row is never removed directly: the provider can
 *   delete the file behind a row it removes, and by the time an entry runs a new file may sit at an
 *   old path.
 *
 * The queue is held in memory and saved after every change, so an entry the process died before
 * finishing runs again on the next launch. The worker reads memory, not the store: a store that
 * cannot be written then costs that resume only, rather than the sync of this process's operations
 * — or handing the worker the same finished entry for ever.
 *
 * Everything that touches Android is passed in, so every branch runs as a JVM unit test;
 * [AndroidMediaIndex] holds the real pieces.
 *
 * @param scan scans the given paths and returns once the scanner has finished every one. A path
 * that no longer exists drops its index row.
 * @param indexedRows up to the given number of the index rows at the given root and under it whose
 * id is above the given one, in id order.
 * @param isMounted whether the storage holding the given path is mounted.
 */
class AndroidMediaIndexSync(
    sdkInt: Int,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val scan: suspend (List<String>) -> Unit,
    private val indexedRows: (root: String, afterId: Long, limit: Int) -> List<IndexedRow>,
    private val isMounted: (String) -> Boolean
) : MediaIndexSync {

    private val indexKeptBySystem = sdkInt >= Build.VERSION_CODES.R

    // Android 10's scanner, handed a path that is gone, drops every row whose path merely starts
    // with it: a gone `Trip` takes the rows of `Trip 2` with it. Earlier scanners match the exact
    // path only.
    private val goneFolderScanDropsSiblings = sdkInt == Build.VERSION_CODES.Q

    // Guarded by itself. The entries skipped this process sit at its head, ahead of every entry
    // still to run: only the worker removes or skips one, always the first it has not skipped,
    // while operations only append.
    private val queue = mutableListOf<Entry>()
    private val loaded = CompletableDeferred<Unit>()
    private val saveLock = Mutex()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** Resumes the saved queue and starts the worker. Called once, at app start. */
    fun start() {
        scope.launch {
            if (indexKeptBySystem) {
                dataStore.editSafely(OPERATION_CLEAR) { preferences -> preferences.remove(KEY_QUEUE) }
                return@launch
            }

            val saved = dataStore.readSafely(OPERATION_READ, emptyList()) { preferences ->
                parseQueue(preferences[KEY_QUEUE])
            }
            // Ahead of anything queued while the store was being read, which is newer.
            synchronized(queue) { queue.addAll(0, saved) }
            loaded.complete(Unit)
            work()
        }
    }

    override fun afterOperation(created: List<String>, removed: List<String>) {
        if (indexKeptBySystem || (created.isEmpty() && removed.isEmpty())) return

        try {
            // Copied, so a caller reusing its lists cannot change an entry already queued.
            synchronized(queue) { queue.add(Entry(created.toList(), removed.toList())) }
            scope.launch { save() }
            wakeUp.trySend(Unit)
        } catch (_: Exception) {
        }
    }

    private suspend fun work() {
        var skipped = 0
        while (true) {
            val entry = synchronized(queue) { queue.getOrNull(skipped) }
            if (entry == null) {
                wakeUp.receive()
                continue
            }

            val kept = process(entry)
            synchronized(queue) {
                if (kept == null) {
                    queue.removeAt(skipped)
                } else {
                    queue[skipped] = kept
                    skipped++
                }
            }
            save()
        }
    }

    /**
     * Syncs [entry], returning what to keep of it for a later launch, or null once it is done with.
     *
     * A root whose storage is not mounted cannot be read now, so it waits for a later launch —
     * skipped for the rest of this one, so its count is of launches rather than of wake-ups — and
     * is dropped once it has been found unmounted on [MAX_UNMOUNTED_LAUNCHES] launches. The
     * entry's other roots are synced now: a move off a card that was then removed still has its
     * copies on internal storage to index.
     *
     * One that fails is reported and dropped, so it cannot hold up every entry behind it — even
     * for an [Error], such as running out of memory on a vast tree, which would otherwise end the
     * worker and leave the entry at the head of the saved queue for every later launch. Its
     * failure is scrubbed: it may have been raised over one of the user's paths.
     */
    private suspend fun process(entry: Entry): Entry? {
        try {
            val (created, waitingCreated) = entry.created.partition { root -> isMounted(root) }
            val (removed, waitingRemoved) = entry.removed.partition { root -> isMounted(root) }

            val batches = ScanBatches(scan)
            created.forEach { root -> addFilesUnder(File(root), batches) }
            removed.forEach { root -> addGoneRowsUnder(root, batches) }
            batches.flush()

            if (waitingCreated.isEmpty() && waitingRemoved.isEmpty()) return null
            val launches = entry.unmountedLaunches + 1
            return if (launches < MAX_UNMOUNTED_LAUNCHES) Entry(waitingCreated, waitingRemoved, launches) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ErrorReporter.warning(e.scrubbed(), OPERATION_SYNC)
        }
        return null
    }

    /**
     * Every index row at or under [root] whose file is gone, read [INDEX_PAGE_SIZE] rows at a time
     * so a vast tree is never held whole. Each page is read from a cursor closed before its rows
     * are scanned: a scan drops rows, and a table that shrinks under an open cursor fails to read.
     * The next page starts after the last row's id, which a scan of earlier rows cannot move.
     *
     * On Android 10 a folder's row is left in the index rather than scanned, which would also drop
     * the rows of every folder whose name starts with its own — the rename's target among them.
     * Galleries list photos, videos and music, not folders. A gone file's row is still scanned: its
     * path only prefixes names that begin with its whole name, extension included.
     */
    private suspend fun addGoneRowsUnder(root: String, batches: ScanBatches) {
        var afterId = 0L
        do {
            currentCoroutineContext().ensureActive()

            val page = indexedRows(root, afterId, INDEX_PAGE_SIZE)
            page.forEach { row ->
                if (row.isFolder && goneFolderScanDropsSiblings) return@forEach
                row.path?.let { path -> if (!File(path).exists()) batches.add(path) }
            }
            afterId = page.lastOrNull()?.id ?: afterId
        } while (page.size == INDEX_PAGE_SIZE)
    }

    /** Every file at or under [root], links skipped. */
    private suspend fun addFilesUnder(root: File, batches: ScanBatches) {
        val pending = ArrayDeque(listOf(root))
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val file = pending.removeLast()
            when {
                file.isSymlink() -> Unit
                file.isDirectory -> file.listFiles()?.let { pending.addAll(it) }
                file.isFile -> batches.add(file.path)
            }
        }
    }

    /** Writes the queue as it is now; waits for the saved one to be read first, or it would be lost. */
    private suspend fun save() {
        loaded.await()
        saveLock.withLock {
            val entries = synchronized(queue) { queue.toList() }
            dataStore.editSafely(OPERATION_WRITE) { preferences ->
                if (entries.isEmpty()) {
                    preferences.remove(KEY_QUEUE)
                } else {
                    preferences[KEY_QUEUE] = serializeQueue(entries)
                }
            }
        }
    }

    /**
     * Each entry as its unmounted launches, the sizes of its two lists and then their paths, every
     * field separated by [SEPARATOR] — the one character no path can hold, so none needs escaping.
     * Hand-rolled rather than JSON like the recents list: `org.json` is a stub in JVM unit tests,
     * where the queue has to round-trip too.
     */
    private fun serializeQueue(entries: List<Entry>): String = entries.flatMap { entry ->
        listOf(
            entry.unmountedLaunches.toString(),
            entry.created.size.toString(),
            entry.removed.size.toString()
        ) + entry.created + entry.removed
    }.joinToString(SEPARATOR)

    /** A value that does not parse reads as an empty queue: losing a sync costs a lagging gallery. */
    private fun parseQueue(value: String?): List<Entry> {
        if (value == null) return emptyList()
        return try {
            val fields = value.split(SEPARATOR).iterator()
            val entries = mutableListOf<Entry>()
            while (fields.hasNext()) {
                val unmountedLaunches = fields.next().toInt()
                val createdCount = fields.next().toInt()
                val removedCount = fields.next().toInt()
                val created = fields.nextPaths(createdCount)
                val removed = fields.nextPaths(removedCount)
                entries.add(Entry(created, removed, unmountedLaunches))
            }
            entries
        } catch (e: Exception) {
            ErrorReporter.error(e.scrubbed(), OPERATION_LOAD)
            emptyList()
        }
    }

    /** Read one at a time rather than allocated up front, so a corrupt count cannot exhaust memory. */
    private fun Iterator<String>.nextPaths(count: Int): List<String> {
        require(count >= 0)
        return buildList { repeat(count) { add(next()) } }
    }

    /** One operation's roots, and on how many launches its storage was found unmounted. */
    private data class Entry(
        val created: List<String>,
        val removed: List<String>,
        val unmountedLaunches: Int = 0
    )

    /** Hands paths to the scanner [SCAN_BATCH_SIZE] at a time, so a large tree is never held whole. */
    private class ScanBatches(private val scan: suspend (List<String>) -> Unit) {
        private var pending = ArrayList<String>(SCAN_BATCH_SIZE)

        suspend fun add(path: String) {
            pending.add(path)
            if (pending.size == SCAN_BATCH_SIZE) flush()
        }

        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = pending
            pending = ArrayList(SCAN_BATCH_SIZE)
            scan(batch)
        }
    }

    /**
     * One row of the media index: its id, the path it describes, which the provider may lack, and
     * whether that path is a folder's.
     */
    data class IndexedRow(val id: Long, val path: String?, val isFolder: Boolean)

    private companion object {
        const val SCAN_BATCH_SIZE = 500
        const val INDEX_PAGE_SIZE = 500
        const val MAX_UNMOUNTED_LAUNCHES = 3

        val KEY_QUEUE = stringPreferencesKey("queue")
        val SEPARATOR = Char(0).toString()

        const val OPERATION_READ = "read_media_index_queue"
        const val OPERATION_LOAD = "load_media_index_queue"
        const val OPERATION_WRITE = "write_media_index_queue"
        const val OPERATION_CLEAR = "clear_media_index_queue"
        const val OPERATION_SYNC = "sync_media_index"
    }
}

/** The Android pieces [AndroidMediaIndexSync] is given in the app. */
internal object AndroidMediaIndex {

    /**
     * Scans [paths] and returns once the scanner has called back for every one of them.
     *
     * The wait is bounded: a scanner whose service never binds never calls back at all, and the
     * worker would wait on it for the rest of the process with every later entry behind it. Past
     * the limit this fails, so the entry is reported and dropped rather than kept for a scanner
     * that is not answering. Generous, because before Android 10 the scanner reads each file's
     * metadata one at a time.
     */
    suspend fun scan(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return

        val finished = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val remaining = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                    if (remaining.decrementAndGet() == 0) continuation.resume(Unit)
                }
            }
        }
        // An ordinary failure rather than the timeout's own cancellation, which the worker would
        // take for its scope being cancelled. A count, never a path: it is reported.
        checkNotNull(finished) { "Media scanner did not finish ${paths.size} paths in time" }
    }

    /**
     * Up to [limit] of the index rows at [root] and under it whose id is above [afterId], in id
     * order, through one selection: [root] exactly, or as a prefix by GLOB. Not LIKE, which is
     * case-insensitive for ASCII, so on a case-sensitive volume it would also match a sibling
     * differing only in case; GLOB compares as bytes, and the wildcards in [root] are escaped so it
     * matches only [root]'s own rows.
     *
     * The page is cut by reading no further rather than by a LIMIT clause, which the provider's
     * sort order does not reliably accept across these releases.
     *
     * A folder's row is told by its MTP format code, kept in a column the SDK does not name but every
     * one of these releases has: a folder's path is gone by the time it is looked up, so the disk
     * cannot say.
     *
     * No cursor is a failure rather than nothing indexed: it is how a query reports a provider it
     * could not reach, and reading it as empty would leave every row behind with nothing reported.
     */
    fun rowsAtAndUnder(
        contentResolver: ContentResolver,
        root: String,
        afterId: Long,
        limit: Int
    ): List<AndroidMediaIndexSync.IndexedRow> {
        val id = MediaStore.Files.FileColumns._ID
        // Deprecated from API 29, but the only way to match rows by path, and this runs up to 29.
        @Suppress("DEPRECATION")
        val data = MediaStore.Files.FileColumns.DATA
        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
            arrayOf(id, data, COLUMN_FORMAT),
            "($data=? OR $data GLOB ?) AND $id>?",
            arrayOf(root, "${root.escapeForGlob()}/*", afterId.toString()),
            "$id ASC"
        ) ?: error("MediaStore returned no cursor for a removed root")

        return cursor.use {
            buildList {
                while (size < limit && it.moveToNext()) {
                    val isFolder = !it.isNull(2) && it.getInt(2) == MtpConstants.FORMAT_ASSOCIATION
                    add(AndroidMediaIndexSync.IndexedRow(it.getLong(0), it.getString(1), isFolder))
                }
            }
        }
    }

    fun isMounted(path: String): Boolean =
        Environment.getExternalStorageState(File(path)) == Environment.MEDIA_MOUNTED

    /**
     * Escapes the wildcards SQLite's `GLOB` recognises so a path is matched literally. `?` matches
     * any single character and `[` opens a character class, both of which occur in file names.
     * GLOB has no escape character; a wildcard is quoted by wrapping it in a single-character class.
     */
    private fun String.escapeForGlob(): String = buildString {
        this@escapeForGlob.forEach { character ->
            when (character) {
                '*', '?', '[' -> append("[").append(character).append("]")
                else -> append(character)
            }
        }
    }

    private const val VOLUME_EXTERNAL = "external"
    private const val COLUMN_FORMAT = "format"
    private const val SCAN_TIMEOUT_MS = 5 * 60 * 1000L
}
