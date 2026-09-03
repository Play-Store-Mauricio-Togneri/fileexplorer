package com.mauriciotogneri.fileexplorer.data.repository

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import com.mauriciotogneri.fileexplorer.data.util.isSymlink
import com.mauriciotogneri.fileexplorer.data.util.storageAnswersAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.os.SystemClock
import java.io.File
import java.io.IOException

/**
 * Walks a whole storage volume and adds up what it finds, by file type.
 *
 * Separate from [FileRepository] rather than another method on it: this is the only walk in the app
 * that visits every file on a volume regardless of what the user is looking at, and it is the only
 * one whose lifetime is a screen the user can cancel.
 */
open class AnalyzerRepository(
    // A test's temp tree is a few dozen files deep at most, so the production interval would let a
    // whole scan finish inside one window and emit nothing but the final snapshot. Taken as a
    // parameter so a test can watch progress arrive instead.
    private val emitIntervalMillis: Long = DEFAULT_EMIT_INTERVAL_MILLIS,
    // Monotonic, not the wall clock: a walk runs for minutes, and an NTP or manual correction
    // backwards mid-scan would make the elapsed comparison below negative and suppress every
    // further emission until the clock caught up — freezing the progress screen on a scan that is
    // in fact still running. A parameter because the unit-test android.jar answers this with a
    // stub that throws.
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
    // Likewise a parameter: see storageAnswersAt's own note on why a JVM test has to be able to
    // state this answer rather than go through StatFs.
    private val storageAnswers: (String) -> Boolean = ::storageAnswersAt
) {

    /**
     * Walks everything under [rootPath] and emits a running tally.
     *
     * Emissions are throttled to one per [emitIntervalMillis]: a volume holds hundreds of thousands
     * of files, and a state update per file would spend more time recomposing the screen than
     * reading the disk. The final tally is always emitted, with [ScanProgress.isComplete] set.
     *
     * Counts every file the process can stat, hidden ones included — the totals are measured against
     * the volume's used space, which counts them too, so skipping them would silently inflate the
     * unaccounted remainder. Symlinks are skipped: a link back up its own tree would never
     * terminate, and a link to a file already visited would be counted twice.
     *
     * A directory that cannot be listed (`Android/data` and `Android/obb` are closed even with All
     * Files Access) is stepped over without failing the scan. That is not an error case but the
     * normal one, and what it leaves out is exactly what the caller reports as system space.
     *
     * A volume that leaves mid-walk looks exactly the same — `File.list()` answers null and raises
     * nothing — so a scan that lost a directory asks the volume directly before claiming to be
     * finished, and throws [StorageUnavailableException] instead of completing. Without that, an
     * ejected SD card drains the queue one null listing at a time and produces a confident chart
     * attributing the entire volume to system space. See [storageAnswersAt] for why the probe is
     * one-directional, and [FileRepository]'s copy and compress paths for the same pairing.
     */
    open fun analyze(rootPath: String): Flow<ScanProgress> = flow {
        val sizes = LongArray(SearchFileType.entries.size)
        var scannedBytes = 0L
        var fileCount = 0
        var currentFolder = rootPath
        var unreadableDirectories = 0
        var lastEmit = 0L
        // A flag rather than seeding lastEmit with a sentinel: the throttle below subtracts, and
        // Long.MIN_VALUE would overflow that subtraction into a negative result that suppresses
        // the very first emission instead of forcing it.
        var hasEmitted = false

        suspend fun emitProgress(isComplete: Boolean) {
            emit(
                ScanProgress(
                    currentFolder = currentFolder,
                    scannedBytes = scannedBytes,
                    fileCount = fileCount,
                    sizesByType = SearchFileType.entries.associateWith { sizes[it.ordinal] },
                    isComplete = isComplete
                )
            )
        }

        // An explicit stack rather than recursion. A volume's directory tree is user-created and its
        // depth is bounded only by the path limit, so a recursive walk puts an attacker-shaped
        // number of frames on the stack; the folders left to visit belong on the heap instead.
        val pending = ArrayDeque<File>()
        pending.addLast(File(rootPath))

        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            currentFolder = directory.absolutePath

            // Names rather than the File[] that listFiles() builds on top of them, for the reason
            // FileRepository.forEachChild carries: the array is held for as long as the directory
            // is being walked, and each element would carry its own joined absolute path.
            //
            // Duplicates are dropped by name: two children of one directory are distinct exactly
            // when their names are, and a listing that repeats one would count those bytes twice.
            // Sized past the 0.75 load factor so a large directory does not rehash on its last
            // insert.
            val names = directory.list() ?: run {
                unreadableDirectories++
                continue
            }
            val seenNames = HashSet<String>(names.size * 4 / 3 + 1)

            for (name in names) {
                currentCoroutineContext().ensureActive()

                if (!seenNames.add(name)) continue

                val file = File(directory, name)
                if (file.isSymlink()) continue

                if (file.isDirectory) {
                    pending.addLast(file)
                    continue
                }

                // Read once and reused: every call is a stat, and this loop runs once per file on
                // the volume.
                val length = file.length()
                sizes[typeOf(file).ordinal] += length
                scannedBytes += length
                fileCount++

                val now = elapsedMillis()
                if (!hasEmitted || now - lastEmit >= emitIntervalMillis) {
                    hasEmitted = true
                    lastEmit = now
                    emitProgress(isComplete = false)
                }
            }
        }

        // Probed only once something was actually lost, the way FileRepository pairs the two: a
        // stat that succeeds proves nothing, so it is worth asking only when the walk already has a
        // gap that the volume leaving would explain.
        if (unreadableDirectories > 0 && !storageAnswers(rootPath)) {
            throw StorageUnavailableException()
        }

        currentFolder = rootPath
        emitProgress(isComplete = true)
    }.flowOn(Dispatchers.IO)

    /**
     * The category [file] counts towards.
     *
     * Goes through [SearchFileType] so that the analyzer and the search filters cannot disagree
     * about what a file is. The [FileItem] is built here rather than by [FileItem.from] because that
     * factory reads the creation time, which costs a `readAttributes` syscall per file on top of the
     * `length()` this already pays — measurable across a volume, and for a field no category rule
     * looks at. Only the name and MIME type are read below.
     */
    private fun typeOf(file: File): SearchFileType {
        val item = FileItem(
            path = file.path,
            name = file.name,
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            createdTime = 0L,
            mimeType = MimeTypeUtil.getMimeType(file)
        )

        // OTHER is declared last and its rule is the negation of the four before it, so the first
        // match is always the right one and the search never falls off the end.
        return SearchFileType.entries.first { it.matches(item) }
    }

    companion object {
        private const val DEFAULT_EMIT_INTERVAL_MILLIS = 100L
    }
}

/**
 * A running tally of a volume scan. [sizesByType] holds an entry for every [SearchFileType],
 * including the ones still at zero, so a caller never has to decide what a missing key means.
 */
/** The volume went away while it was being walked, so the tally it produced describes nothing. */
class StorageUnavailableException : IOException("Storage is no longer available")

@Immutable
data class ScanProgress(
    /** The directory being listed when this snapshot was taken; the volume root once complete. */
    val currentFolder: String,
    val scannedBytes: Long,
    val fileCount: Int,
    val sizesByType: Map<SearchFileType, Long>,
    val isComplete: Boolean
)
