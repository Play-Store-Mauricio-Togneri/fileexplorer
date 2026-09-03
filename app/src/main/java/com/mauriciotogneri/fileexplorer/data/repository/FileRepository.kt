package com.mauriciotogneri.fileexplorer.data.repository

import android.os.Build
import android.os.StatFs
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import coil3.disk.DiskCache
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import com.mauriciotogneri.fileexplorer.data.util.evictThumbnail
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.util.DeleteFailure
import com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome
import com.mauriciotogneri.fileexplorer.data.util.removePath
import com.mauriciotogneri.fileexplorer.data.util.errnoOrNull
import com.mauriciotogneri.fileexplorer.data.util.isSymlink
import com.mauriciotogneri.fileexplorer.data.util.toPathOrNull
import com.mauriciotogneri.fileexplorer.data.util.isStorageUnavailable
import com.mauriciotogneri.fileexplorer.data.util.isNoSpaceLeft
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import com.mauriciotogneri.fileexplorer.data.util.storageAnswersAt
import com.mauriciotogneri.fileexplorer.data.util.thumbnailDiskCacheKeyFor
import com.mauriciotogneri.fileexplorer.util.fileNameStem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

/**
 * @param thumbnailDiskCache where a deleted file's extracted thumbnail is dropped from. Read
 * through a function rather than taken as a value because the loader that owns it is built lazily,
 * so there may be none yet when a repository is constructed. Defaults to the app's own cache and is
 * a parameter so a test can supply its own instead of reaching into the process-wide loader. Listed
 * first so that [onFilesMutated] stays the trailing parameter every caller passes as a lambda.
 *
 * @param onFilesMutated invoked by every operation that adds, removes or rewrites files on disk.
 * The home screen caches each location's total size behind a TTL and nothing else invalidates it,
 * so without this hook a card keeps reporting a pre-delete total until that TTL lapses.
 *
 * Wired here rather than at each ViewModel's call site because this class is the one place every
 * mutation passes through: a new operation added below cannot forget to notify, whereas a new
 * caller could.
 *
 * Fired from a `finally` scoped to the region of each operation that actually writes, once the
 * tree has stopped changing, rather than before the work starts. Invalidating up front does not stay
 * invalidated: a home-screen pass running alongside a slow delete measures the tree as it was, and
 * its sizes land after the invalidation, where they read as fresh for the whole TTL. Firing at the
 * end puts the invalidation after every write the operation makes, and still covers one that failed
 * or was cancelled part-way, which leaves a partly changed tree behind. Operations rejected before
 * they touch the disk (an invalid name, a target outside the allowed roots) do not fire it at all.
 *
 * Best-effort, and deliberately so: every caller wires this to
 * `LocationsCacheSource.clearCache()`, whose write routes through `editSafely` and is absorbed on
 * an `IOException`. Nothing here outlives the operation to retry it, so a swallowed clear leaves
 * the home cards on pre-mutation totals until the TTL lapses. The other invalidation path — an
 * external write, seen as a media notification — does retry, because
 * [LocationsRepository.markSizeCacheStale] puts its mark back when the clear does not land. This
 * one cannot borrow that: each screen builds its own cache source, and the only
 * [LocationsRepository] that reads such a mark is the home screen's own instance, so a mark set
 * from a folder or a viewer would be read by nothing. Closing it would take state outliving every
 * ViewModel, which is not worth it for a window one TTL wide that opens only when the store is
 * already failing.
 */
open class FileRepository(
    private val thumbnailDiskCache: () -> DiskCache? = { AppImageLoader.thumbnailDiskCache },
    /**
     * Takes a file off its path. See [RemoveOutcome] for why an already-absent path is a state of
     * its own rather than a second name for success.
     *
     * A parameter rather than a direct call because [removePath] goes through [android.system.Os],
     * which is a stub that throws on the JVM, and this repository's delete tests run there against
     * real temporary files. Overriding it is the only way those tests can keep deleting something
     * real; the production default is what a device runs, and `FileAccessTest` covers it there.
     *
     * Declared before [onFilesMutated] rather than appended, because every caller passes that one
     * as a trailing lambda and a trailing lambda binds to the last parameter.
     */
    private val removeFile: (File) -> RemoveOutcome = ::removePath,
    private val onFilesMutated: (suspend () -> Unit)? = null
) {

    /**
     * Runs [onFilesMutated] once the tree has stopped changing.
     *
     * NonCancellable because the usual way a long operation ends is the user cancelling it, which
     * still leaves a partly changed tree behind: the hook suspends (it writes to a DataStore), so on
     * a cancelled job it would be cancelled at its first suspension point and the size the home
     * screen has cached would survive the change it no longer describes.
     */
    private suspend fun notifyFilesMutated() {
        val notify = onFilesMutated ?: return
        withContext(NonCancellable) { notify() }
    }

    /**
     * Lists a directory's entries, hidden ones optionally filtered out, duplicates dropped
     * (first occurrence wins) and sorted by [sortMode].
     *
     * Built in a single pass into one list rather than a chain of filter/map/distinct/sort steps.
     * A directory with hundreds of thousands of entries is the app's largest allocation by far,
     * and each intermediate collection multiplies the peak — the transient spike, not the result,
     * is what runs the heap out.
     *
     * Walks `list()`'s names and builds one child [File] per step rather than taking the array
     * `listFiles()` returns, for the reason [forEachChild] carries: `listFiles()` calls `list()` and
     * then materialises an N-element `File[]` on top of it, so a large directory pays for a second
     * huge contiguous array plus a `File` per entry on top of the names — and that second array is
     * the kind of allocation a fragmented heap refuses while still reporting megabytes free. Only
     * the [FileItem]s are meant to outlive the pass.
     *
     * Duplicates are dropped by name, not by absolute path: two children of one directory are
     * distinct exactly when their names are, so the set can hold the names `list()` already returned
     * instead of a path per entry. Hidden entries are filtered before the set, so a listing that
     * hides them does not retain them either.
     */
    open suspend fun listFiles(
        path: String,
        showHidden: Boolean,
        sortMode: SortMode
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val directory = File(path)
        val names = directory.list() ?: return@withContext emptyList()
        val items = ArrayList<FileItem>(names.size)
        // Sized past the 0.75 load factor: HashSet(n) is guaranteed to rehash on the nth insert,
        // and for a large directory that doubling holds two tables at once.
        val seenNames = HashSet<String>(names.size * 4 / 3 + 1)

        for (index in names.indices) {
            val name = names[index]

            if (!showHidden && name.startsWith(".")) continue
            if (!seenNames.add(name)) continue

            items.add(FileItem.from(File(directory, name)))
        }

        sortInPlace(items, sortMode)
        items
    }

    /**
     * Counts a directory's direct children, or null if [path] cannot be read. Files and
     * subdirectories both count; hidden entries count only when [showHidden] does, applying the same
     * name filter [listFiles] does so a folder does not report entries the rows below it leave out.
     *
     * Filtering costs no extra I/O: the names are already in the array `list()` returned. The one
     * thing [listFiles] does that this does not is drop duplicate names, which needs a set per
     * directory — too much to allocate for every folder on screen, and this count would exceed the
     * rows only on a filesystem that lists a name twice.
     *
     * Intentionally runs on the caller's dispatcher (no internal withContext) so the caller can
     * bound concurrency with a limited dispatcher; must be called off the main thread.
     */
    open suspend fun countChildren(path: String, showHidden: Boolean): Int? {
        val names = File(path).list() ?: return null

        if (showHidden) return names.size

        var count = 0
        for (index in names.indices) {
            if (!names[index].startsWith(".")) count++
        }

        return count
    }

    /**
     * Returns [files] ordered by [sortMode], leaving the input untouched. [listFiles] sorts its own
     * list in place instead, so this exists to exercise the ordering on a caller-supplied list.
     */
    @VisibleForTesting
    fun sortFiles(files: List<FileItem>, sortMode: SortMode): List<FileItem> {
        val sorted = ArrayList(files)
        sortInPlace(sorted, sortMode)
        return sorted
    }

    /**
     * Orders [files] in place: directories first, then [sortMode]'s ordering within each group.
     * Folding the directory flag into the comparator sorts the list in one stable pass, instead of
     * splitting it into two groups and concatenating the sorted halves.
     */
    private fun sortInPlace(files: MutableList<FileItem>, sortMode: SortMode) {
        when (sortMode) {
            SortMode.NAME_ASC -> sortByNameInPlace(files, descending = false)
            SortMode.NAME_DESC -> sortByNameInPlace(files, descending = true)
            SortMode.SIZE_ASC -> files.sortWith(compareBy({ !it.isDirectory }, { it.size }))
            SortMode.SIZE_DESC -> files.sortWith(
                compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size }
            )
            SortMode.DATE_ASC -> files.sortWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            SortMode.DATE_DESC -> files.sortWith(
                compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified }
            )
        }
    }

    /**
     * Sorts by name using a decorate-sort-undecorate pass so each name is lowercased once (O(n))
     * rather than on every comparison (O(n log n)), as `compareBy { it.name.lowercase() }` would.
     * The sort stays stable, so entries with equal lowercased names keep their input order.
     */
    private fun sortByNameInPlace(files: MutableList<FileItem>, descending: Boolean) {
        val decorated = Array(files.size) { index ->
            val file = files[index]
            file.name.lowercase(Locale.ROOT) to file
        }
        val comparator: Comparator<Pair<String, FileItem>> = if (descending) {
            compareBy<Pair<String, FileItem>> { !it.second.isDirectory }
                .thenByDescending { it.first }
        } else {
            compareBy({ !it.second.isDirectory }, { it.first })
        }
        decorated.sortWith(comparator)

        for (index in files.indices) {
            files[index] = decorated[index].second
        }
    }

    suspend fun createFolder(parentPath: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            if (name.contains('/') || name.contains('\\')) {
                return@withContext false
            }

            if (isPathTooLong(name, parentPath)) {
                return@withContext false
            }

            val parent = File(parentPath)
            val newFolder = File(parent, name)
            val parentCanonical = parent.canonicalPath

            if (!newFolder.canonicalPath.startsWith(parentCanonical + File.separator) &&
                newFolder.canonicalPath != parentCanonical
            ) {
                return@withContext false
            }

            try {
                newFolder.mkdir()
            } finally {
                notifyFilesMutated()
            }
        }

    suspend fun rename(file: FileItem, newName: String): RenameResult? = withContext(Dispatchers.IO) {
        if (newName.contains('/') || newName.contains('\\')) {
            return@withContext null
        }

        val sourceFile = File(file.path)
        val parentDir = sourceFile.parentFile ?: return@withContext null

        if (isPathTooLong(newName, parentDir.absolutePath)) {
            return@withContext null
        }
        val targetFile = File(parentDir, newName)

        val parentCanonical = parentDir.canonicalPath
        if (!targetFile.canonicalPath.startsWith(parentCanonical + File.separator) &&
            targetFile.canonicalPath != parentCanonical
        ) {
            return@withContext null
        }

        val isCaseOnlyRename = sourceFile.name.equals(newName, ignoreCase = true) &&
            sourceFile.name != newName

        val thumbnailKey = thumbnailKeyFor(sourceFile)
        val result = try {
            if (isCaseOnlyRename) {
                renameCaseOnly(sourceFile, targetFile)
            } else {
                renameRegular(sourceFile, targetFile)
            }
        } finally {
            notifyFilesMutated()
        }

        // The file kept its content but not its name, so what was cached under the old one is dead.
        // Both paths above restore the source and return null if they fail, so a rename that did
        // not happen leaves its thumbnail where it is.
        if (result != null) {
            dropThumbnail(thumbnailKey)
        }
        result
    }

    private fun renameCaseOnly(sourceFile: File, targetFile: File): RenameResult? {
        val parentDir = sourceFile.parentFile ?: return null
        val tempFile = File(parentDir, ".tmp_rename_${System.currentTimeMillis()}_${sourceFile.name}")

        return try {
            if (!sourceFile.renameTo(tempFile)) {
                return null
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.renameTo(sourceFile)
                return null
            }
            RenameResult(
                oldPath = sourceFile.absolutePath,
                newPath = targetFile.absolutePath,
                isCaseOnlyRename = true
            )
        } catch (_: Exception) {
            if (tempFile.exists()) {
                tempFile.renameTo(sourceFile)
            }
            null
        }
    }

    private fun renameRegular(
        sourceFile: File,
        targetFile: File
    ): RenameResult? {
        // Reject an existing target up front: both the ATOMIC_MOVE below and the java.io renameTo
        // it falls back to map to rename(2), which silently replaces an existing regular-file
        // target (including hidden dotfiles the collision dialog never sees), so the existence
        // guard must cover every path out of this function.
        if (targetFile.exists()) {
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sourcePath = sourceFile.toPathOrNull()
            val targetPath = targetFile.toPathOrNull()

            // Names that cannot be represented as a Path fall through to the java.io rename below.
            if (sourcePath != null && targetPath != null) {
                return try {
                    Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE)
                    RenameResult(
                        oldPath = sourceFile.absolutePath,
                        newPath = targetFile.absolutePath
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    try {
                        Files.move(sourcePath, targetPath)
                        RenameResult(
                            oldPath = sourceFile.absolutePath,
                            newPath = targetFile.absolutePath
                        )
                    } catch (_: FileAlreadyExistsException) {
                        null
                    } catch (_: IOException) {
                        null
                    }
                } catch (_: FileAlreadyExistsException) {
                    null
                } catch (_: IOException) {
                    null
                }
            }
        }

        return if (sourceFile.renameTo(targetFile)) {
            RenameResult(
                oldPath = sourceFile.absolutePath,
                newPath = targetFile.absolutePath
            )
        } else {
            null
        }
    }

    /**
     * Deletes every item in [files], reporting each one's outcome so that the caller can tell the
     * user how much came away and tell MediaStore only about what this call actually removed.
     *
     * Every item is attempted even after one fails. This used to short-circuit — `files.all {}`
     * stops at the first false — so a multi-selection whose first item could not be deleted left
     * the rest untouched behind a message that named none of them. [deleteRecursive] never had
     * that problem — it deletes the directory itself whatever its children answered — so only the
     * loop over the top level did.
     *
     * A root is [DeleteResult.removedPaths] only when the walk unlinked something under it and left
     * nothing behind. A root nothing was ever there for is [DeleteResult.alreadyAbsentPaths]: the
     * user's request is met, but see [RemoveOutcome] for why the caller must not report it to
     * MediaStore as a deletion.
     */
    suspend fun delete(files: List<FileItem>): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val removedPaths = mutableListOf<String>()
            val alreadyAbsentPaths = mutableListOf<String>()
            var failedCount = 0
            var failureErrno: Int? = null

            files.forEach { item ->
                val outcome = deleteRecursive(File(item.path))

                when {
                    outcome.failureErrno != null -> {
                        failedCount++

                        if (failureErrno == null) {
                            failureErrno = outcome.failureErrno
                        }
                    }

                    outcome.anyRemoved -> removedPaths.add(item.path)
                    else -> alreadyAbsentPaths.add(item.path)
                }
            }

            DeleteResult(removedPaths, alreadyAbsentPaths, failedCount, failureErrno)
        } finally {
            notifyFilesMutated()
        }
    }

    /**
     * Removes [file] and everything under it, answering null when nothing is left on any of those
     * paths and otherwise the errno behind the first failure.
     *
     * Depth-first, so the answer is a child's errno where there is one and the directory's only
     * where there is not. That ordering is what makes the report useful: a read-only volume fails
     * every leaf with EROFS and then fails the directory with ENOTEMPTY because those leaves
     * survived, and only the first of those names the cause.
     */
    private fun deleteRecursive(file: File): TreeOutcome {
        var childErrno: Int? = null
        var anyRemoved = false

        if (file.isDirectory && !file.isSymlink()) {
            file.forEachChild { child ->
                val subtree = deleteRecursive(child)

                if (childErrno == null) {
                    childErrno = subtree.failureErrno
                }
                if (subtree.anyRemoved) {
                    anyRemoved = true
                }
            }
        }

        // Not folded into a `?:` over the children's answer: that would stop attempting the
        // directory as soon as one child failed, which is the short-circuit this walk has always
        // avoided.
        val own = deleteAndDropThumbnail(file)

        if (own is RemoveOutcome.Removed) {
            anyRemoved = true
        }

        return TreeOutcome(childErrno ?: (own as? RemoveOutcome.Failed)?.errno, anyRemoved)
    }

    /**
     * What [deleteRecursive] found over one subtree: the errno behind its first failure, and
     * whether anything under it was actually unlinked rather than already absent.
     *
     * The two are independent. A tree can come away entirely without this call removing a single
     * node — every path in it had already gone — and a tree that failed can still have had most of
     * itself removed.
     */
    private data class TreeOutcome(val failureErrno: Int?, val anyRemoved: Boolean)

    /**
     * [deleteRecursive] for callers that only need to know whether the tree came away. True also
     * for a path that already held nothing, since that is what a delete is asked for; a caller that
     * needs "this call removed it" — the extraction rollback, which reports what it removed to a
     * prefix-matching MediaStore delete — has to test existence itself first.
     */
    private fun deleteTree(file: File): Boolean = deleteRecursive(file).failureErrno == null

    /** [deleteAndDropThumbnail] for callers that only need to know whether the file came away. */
    private fun deleted(file: File): Boolean = deleteAndDropThumbnail(file) !is RemoveOutcome.Failed

    /**
     * Deletes [file] and drops the thumbnail cached for it, which would otherwise sit in the cache
     * keyed to a path nothing occupies any more until eviction reclaimed it.
     *
     * The thumbnail is dropped only for a path this call actually cleared. An already-absent one
     * has nothing to drop: the cache key includes the modification time, which a path holding
     * nothing cannot answer with, so the entry it would build matches no cached thumbnail.
     *
     * The directory is still attempted after a child failed — [deleteRecursive] calls this
     * unconditionally — because a directory whose remaining entries were removed by something else
     * in the meantime can still go, and the failure it answers with is the one that says the tree
     * is not gone.
     */
    private fun deleteAndDropThumbnail(file: File): RemoveOutcome {
        val thumbnailKey = thumbnailKeyFor(file)
        val outcome = removeFile(file)

        if (outcome is RemoveOutcome.Removed) {
            dropThumbnail(thumbnailKey)
        }
        return outcome
    }

    /**
     * The key the thumbnail cached for [file] sits under, read while the file still answers to its
     * path because the key includes its modification time. Every operation that takes a file off a
     * path — a delete, and the source side of a move or a rename — captures this first and drops it
     * afterwards, so an operation that then fails leaves a still-correct thumbnail alone.
     *
     * Null when there is no cache yet, and for anything without an extracted thumbnail — nearly
     * every file, and settled from the name alone, so walking a large tree costs no extra syscall.
     */
    private fun thumbnailKeyFor(file: File): String? =
        if (thumbnailDiskCache() != null) thumbnailDiskCacheKeyFor(file) else null

    /** Drops what [thumbnailKeyFor] captured, once its file has left that path for good. */
    private fun dropThumbnail(thumbnailKey: String?) {
        if (thumbnailKey != null) {
            evictThumbnail(thumbnailDiskCache(), thumbnailKey)
        }
    }

    fun deleteWithProgress(files: List<FileItem>): Flow<DeleteProgress> = flow {
        val totalFiles = files.sumOf { File(it.path).totalFileCount() }
        var deletedFiles = 0
        var failedFiles = 0
        // A counter rather than a flag, for the same reason [failedFiles] is one: the per-root
        // classification below compares it before and after each root, and a monotonic boolean
        // would answer "did this root fail structurally?" with `false` for every root after the
        // first one that did.
        var structuralFailures = 0
        var removedNodes = 0
        // Roots, not nodes: the caller routes MediaStore per selected root, and a path per
        // descendant is unbounded in the size of the tree — the retention this whole walk is
        // shaped to avoid.
        val removedRootPaths = mutableListOf<String>()
        val absentRootPaths = mutableListOf<String>()
        // Only the first, for the reason the transfer walk's `skippedErrno` gives: one int says
        // which cause the report has to account for, and a count per errno would be a histogram of
        // the user's own storage failures for no extra answer.
        var failureErrno: Int? = null

        suspend fun deleteRecursiveWithProgress(file: File) {
            currentCoroutineContext().ensureActive()

            val isSymlink = file.isSymlink()
            val isDirectory = file.isDirectory && !isSymlink

            if (isDirectory) {
                file.forEachChild { child ->
                    deleteRecursiveWithProgress(child)
                }
            }

            emit(
                DeleteProgress(
                    currentFile = file.name,
                    deletedFiles = deletedFiles,
                    totalFiles = totalFiles,
                    failedFiles = failedFiles
                )
            )

            val outcome = deleteAndDropThumbnail(file)
            val deleted = outcome !is RemoveOutcome.Failed

            if (outcome is RemoveOutcome.Removed) {
                removedNodes++
            }
            if (failureErrno == null) {
                failureErrno = (outcome as? RemoveOutcome.Failed)?.errno
            }

            // Only leaf files contribute to the progress totals, matching `totalFiles`
            // (computed via the leaf-only `totalFileCount`). Directories and symlinks are
            // still deleted above, just not counted — otherwise the numerator could exceed
            // the denominator and the partial-success toast would over-report failures.
            if (!isDirectory && !isSymlink) {
                if (deleted) {
                    // A leaf something else had already taken counts here too: the fraction has to
                    // keep advancing over a tree being emptied underneath the walk, and nothing
                    // downstream needs the two apart — the caller reports selected roots, which
                    // [removedRootPaths] and [absentRootPaths] already separate.
                    deletedFiles++
                } else {
                    failedFiles++
                }
            } else if (!deleted) {
                // A directory or symlink that could not be removed (e.g. a read-only parent).
                // Tracked apart from the leaf-file counts so the caller can still tell the tree
                // was not fully deleted without distorting the progress fraction.
                structuralFailures++
            }
        }

        // The hook is scoped to the region that writes, rather than hung off the flow's completion:
        // everything above only reads, so an operation that fails before this point has nothing to
        // invalidate. `finally` covers the user cancelling part-way, which leaves a partly deleted
        // tree behind.
        try {
            files.forEach { fileItem ->
                val removedBefore = removedNodes
                val failedBefore = failedFiles
                val structuralBefore = structuralFailures

                deleteRecursiveWithProgress(File(fileItem.path))

                // Sorted the way the small path's roots are, and for the same reason: only a root
                // this walk emptied may be reported to MediaStore, whose row delete matches as a
                // prefix and whose row removal makes a media provider unlink the backing file. A
                // root nothing was ever at is scanned instead. See [RemoveOutcome].
                if (failedFiles == failedBefore && structuralFailures == structuralBefore) {
                    if (removedNodes > removedBefore) {
                        removedRootPaths.add(fileItem.path)
                    } else {
                        absentRootPaths.add(fileItem.path)
                    }
                }
            }

            emit(
                DeleteProgress(
                    currentFile = "",
                    deletedFiles = deletedFiles,
                    totalFiles = totalFiles,
                    failedFiles = failedFiles,
                    structuralDeleteFailed = structuralFailures > 0,
                    removedRootPaths = removedRootPaths,
                    absentRootPaths = absentRootPaths,
                    failureErrno = failureErrno,
                    isComplete = true
                )
            )
        } finally {
            notifyFilesMutated()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * @param onPartialTransfer invoked with the paths this transfer created, the source paths it
     * deleted, the source paths it found already absent, and whether any source failed to delete —
     * the batch that had not yet been handed to the caller when the transfer failed or was
     * cancelled. The
     * batching in [CopyProgress.createdPaths] holds up to [MEDIA_PATH_BATCH_SIZE] of each back for
     * the next emission, and a failure reaches no emission — so without this the last files of a
     * failed move keep MediaStore rows for sources that are gone and have none for the copies that
     * arrived. Reported through a callback rather than a final emission for the reason
     * [uncompressFile] reports its rollback through one: emitting once the flow is already failing
     * races the channel [flowOn] puts between this walk and its collector, and lands only sometimes.
     */
    fun copyFiles(
        sources: List<FileItem>,
        targetDir: String,
        deleteAfter: Boolean,
        allowedRoots: List<String>,
        onPartialTransfer: suspend (
            created: List<String>,
            deleted: List<String>,
            absent: List<String>,
            sourceDeleteFailed: Boolean
        ) -> Unit = { _, _, _, _ -> }
    ): Flow<CopyProgress> = flow {
        val targetFolder = File(targetDir)
        if (!isWithinAllowedRoots(targetFolder, allowedRoots)) {
            throw SecurityException("Target directory is outside allowed storage paths")
        }

        val totalBytes = sources.sumOf { File(it.path).totalSize() }
        val totalFiles = sources.sumOf { File(it.path).totalFileCount() }
        var copiedBytes = 0L
        var copiedFiles = 0
        var skippedFiles = 0
        // Only the first: one int says which errno the set has to account for, and keeping a
        // count per errno would be a histogram of the user's own storage failures for no extra
        // answer.
        var skippedErrno: Int? = null
        var sourceDeleteFailed = false
        // Counted apart from [skippedFiles], which is leaf files and has to stay equal to what
        // `totalFileCount` tallied over the same listing. A directory that cannot be listed is
        // neither in that total nor a file, and reporting it as one would put the walk's own
        // arithmetic out. Nothing is raised on that path — `list()` just answers null — so this is
        // the only trace a subtree that was never walked leaves behind.
        var unreadableDirectories = 0
        // Reported to the caller in batches and started fresh after each one, rather than kept
        // until the transfer ends: one absolute path per copied file is unbounded in the size of
        // the tree and has run small-heap devices out of memory. Reassigned rather than cleared so
        // that a batch already handed to the caller is never mutated afterwards.
        var createdPaths = ArrayList<String>()
        var deletedSourcePaths = ArrayList<String>()
        var absentSourcePaths = ArrayList<String>()

        suspend fun copyRecursive(source: File, targetParent: File) {
            currentCoroutineContext().ensureActive()

            if (source.isSymlink()) {
                if (deleteAfter && !deleted(source)) sourceDeleteFailed = true
                return
            }

            if (source.isDirectory) {
                val newDir = File(targetParent, source.name)
                newDir.mkdirs()
                val skippedBefore = skippedFiles
                source.forEachChild(onListingFailed = { unreadableDirectories++ }) { child ->
                    copyRecursive(child, newDir)
                }
                newDir.copyLastModifiedFrom(source)
                // The delete is still attempted — a subtree whose skips were all vanished files is
                // empty and does come away — but a directory left standing by a file this walk
                // deliberately skipped must not raise [CopyProgress.sourceDeleteFailed]. That flag
                // means the copy finished and only the cleanup did not, which is what the toast
                // built on it tells the user; here the copy is the part that did not finish, and
                // the flag would both make that claim and, being sticky, suppress the MediaStore
                // notification for every source the rest of the move really did delete.
                if (deleteAfter && !deleted(source) && skippedFiles == skippedBefore) {
                    sourceDeleteFailed = true
                }
            } else {
                // A file the listing named but that cannot be opened is skipped rather than
                // failing the transfer, for the reason [compressFiles] gives at the same point and
                // on the same [isStorageUnavailable] test. What is specific to a transfer is the
                // move: a skipped source keeps its original, because the delete below is reached
                // only by a file that was copied first, and the directory branch above will not
                // report the parent it leaves standing as a source that failed to delete.
                //
                // Opened before the destination is reserved, so a source that cannot be read
                // leaves no empty placeholder behind under the name it would have taken —
                // [getUniqueTargetFile] creates the file it returns.
                val input = try {
                    source.inputStream()
                } catch (e: FileNotFoundException) {
                    // The volume going away rather than this one file has to fail the transfer,
                    // not skip every remaining source and report a partial success. Wrapped here
                    // rather than rethrown, because the try that wraps the transfer's own I/O
                    // failures starts below this line and would not catch it. It is the only
                    // classify site here that does not re-test [isNoSpaceLeft] first, which is
                    // safe because a read-only open cannot return ENOSPC — and moving this throw
                    // down into that try to make it uniform would put the open back above
                    // [getUniqueTargetFile] and undo the ordering the comment above protects.
                    if (e.isStorageUnavailable()) {
                        throw FileTransferIOException("Failed to copy file", e.scrubbed())
                    }
                    if (skippedErrno == null) skippedErrno = e.errnoOrNull()
                    skippedFiles++
                    return
                }

                // Reserved outside the try below so that `targetFile` is in scope for the catch
                // that deletes it, and closed by hand here because that try starts after this
                // line. The try has to enclose `input.use` rather than sit inside it: closing the
                // source is an I/O site of its own — a volume going away under an open descriptor
                // fails at `close(2)` — and that close was inside the wrapped region before the
                // open moved ahead of this call.
                val targetFile = try {
                    getUniqueTargetFile(targetParent, source.name)
                } catch (e: Throwable) {
                    // The close is an I/O site too, and letting it throw would replace `e` —
                    // the classified [InsufficientStorageException] or
                    // [DestinationNotWritableException] the caller catches to tell the user what
                    // to do about it. Attach the close failure instead and rethrow the original.
                    //
                    // Attached through [scrubbed] for the reason the transfer's own wrapping
                    // gives below: the property is the producer's to keep and holds for the
                    // whole object, and a printed stack trace walks the suppressed list the same
                    // way a report walks the cause chain.
                    runCatching { input.close() }.onFailure { e.addSuppressed(it.scrubbed()) }
                    throw e
                }

                try {
                    input.use { stream ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (stream.read(buffer).also { bytes = it } >= 0) {
                                output.write(buffer, 0, bytes)
                                copiedBytes += bytes
                                emit(
                                    CopyProgress(
                                        currentFile = source.name,
                                        copiedFiles = copiedFiles,
                                        totalFiles = totalFiles,
                                        copiedBytes = copiedBytes,
                                        totalBytes = totalBytes,
                                        skippedFiles = skippedFiles,
                                        skippedErrno = skippedErrno,
                                        unreadableDirectories = unreadableDirectories
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Whatever went wrong — I/O error, full device, or the user cancelling —
                    // the destination now holds a truncated copy (or the empty file that
                    // reserved the name), which is indistinguishable from a complete one in
                    // the file list. Remove it; files copied before this one are complete and
                    // stay.
                    targetFile.delete()

                    // An IOException once the stream is open is environmental, not an app bug:
                    // removable storage unmounted mid-copy (EIO/ENODEV), a failing flash chip,
                    // and the close that ends the transfer, which fails for the same reasons.
                    // A source that vanished no longer arrives here — it fails at the open above
                    // and is skipped. Everything else — cancellation included — is rethrown
                    // unchanged so callers keep seeing its own type.
                    //
                    // The message names the operation and never the file, though `source` is
                    // right here. Not because this one is reported — no consumer of it calls
                    // ErrorReporter today — but because a file name is personal data and
                    // nothing at the throw site can see whether a caller reports what it
                    // catches. The property is kept by the producer rather than by a catch
                    // clause staying put, and it holds for the whole object: the platform
                    // exception's own message is the absolute path, and a report follows the
                    // cause chain past the scrubbed message, so it is attached through
                    // [scrubbed] rather than directly.
                    if (e is IOException) {
                        if (e.isNoSpaceLeft()) {
                            throw InsufficientStorageException("Not enough disk space", e.scrubbed())
                        }
                        throw FileTransferIOException("Failed to copy file", e.scrubbed())
                    }

                    throw e
                }
                targetFile.copyLastModifiedFrom(source)
                copiedFiles++
                createdPaths.add(targetFile.absolutePath)
                if (deleteAfter) {
                    // Split three ways rather than two. A source something else removed while the
                    // copy ran satisfies the move — the path holds nothing and the copy is made —
                    // but it must not join `deletedSourcePaths`, which the caller hands to
                    // MediaStore as paths whose files are gone. Scanning it instead drops a stale
                    // row just the same and, if the path has been taken over since, re-indexes what
                    // is there now rather than unlinking it. See [RemoveOutcome].
                    when (deleteAndDropThumbnail(source)) {
                        is RemoveOutcome.Removed -> deletedSourcePaths.add(source.absolutePath)
                        is RemoveOutcome.AlreadyAbsent -> absentSourcePaths.add(source.absolutePath)
                        is RemoveOutcome.Failed -> sourceDeleteFailed = true
                    }
                }

                // Only the created paths are measured: a move deletes at most one source per
                // file it creates, so bounding one bounds the other.
                if (createdPaths.size >= MEDIA_PATH_BATCH_SIZE) {
                    emit(
                        CopyProgress(
                            currentFile = source.name,
                            copiedFiles = copiedFiles,
                            totalFiles = totalFiles,
                            copiedBytes = copiedBytes,
                            totalBytes = totalBytes,
                            // Carried on every batch, not just the last one: the flag is
                            // sticky, so once a source has failed to delete the caller must
                            // stop being told that the sources it is handed are safe to
                            // report as removed.
                            sourceDeleteFailed = sourceDeleteFailed,
                            createdPaths = createdPaths,
                            deletedSourcePaths = deletedSourcePaths,
                            absentSourcePaths = absentSourcePaths,
                            skippedFiles = skippedFiles
                        )
                    )
                    createdPaths = ArrayList()
                    deletedSourcePaths = ArrayList()
                    absentSourcePaths = ArrayList()
                }
            }
        }

        // Scoped to the region that writes: the allowed-roots guard and the size tallies above
        // touch nothing, so a target rejected there must leave a still-correct cached size alone.
        try {
            sources.forEach { source ->
                copyRecursive(File(source.path), targetFolder)
            }

            // Only once, and only after something was already lost. A walk that covered everything
            // has nothing to check, and the files it skipped are the ordinary case — `Android/data`
            // denies them on a volume that is perfectly healthy. What this separates is that case
            // from the one the counts cannot describe: a volume that left mid-walk, whose skipped
            // files and unlisted directories are the user's data rather than the OS's.
            // Only when nothing made it across. A transfer that copied files and then lost the
            // volume is a partial success and says so with its own counts; failing it would tell
            // the user "Move failed" over files that are sitting at the destination, and on a move
            // whose originals are already gone that is the least useful thing they could be told.
            if (copiedFiles == 0 &&
                (skippedFiles > 0 || unreadableDirectories > 0) &&
                !storageStillAnswers(sources.map { it.path }, allowedRoots)
            ) {
                throw FileTransferIOException("Source storage is no longer available")
            }

            emit(
                CopyProgress(
                    currentFile = "",
                    copiedFiles = copiedFiles,
                    totalFiles = totalFiles,
                    copiedBytes = copiedBytes,
                    totalBytes = totalBytes,
                    isComplete = true,
                    sourceDeleteFailed = sourceDeleteFailed,
                    createdPaths = createdPaths,
                    deletedSourcePaths = deletedSourcePaths,
                    absentSourcePaths = absentSourcePaths,
                    skippedFiles = skippedFiles,
                    skippedErrno = skippedErrno,
                    unreadableDirectories = unreadableDirectories
                )
            )
        } catch (e: Throwable) {
            // NonCancellable for the reason [uncompressFile]'s rollback callback is: the usual way
            // a transfer ends early is the user cancelling it, and a suspending callback would then
            // be cancelled at its first suspension point — leaving the caller's view of the files
            // that did move as it was. Guarded because a callback that threw here would replace the
            // failure being reported, a cancellation included, with its own.
            if (createdPaths.isNotEmpty() || deletedSourcePaths.isNotEmpty() ||
                absentSourcePaths.isNotEmpty()
            ) {
                withContext(NonCancellable) {
                    // The flag goes with the paths rather than being read from the caller's own
                    // view of the emissions: that view is written on the collector and would be
                    // read here on the walk's thread, across the channel [flowOn] puts between
                    // them, with nothing ordering the two.
                    runCatching {
                        onPartialTransfer(
                            createdPaths,
                            deletedSourcePaths,
                            absentSourcePaths,
                            sourceDeleteFailed
                        )
                    }
                }
            }

            throw e
        } finally {
            notifyFilesMutated()
        }
    }.flowOn(Dispatchers.IO)

    private fun getUniqueTargetFile(targetDir: File, name: String): File {
        var targetFile = File(targetDir, name)
        if (createDestinationFile(targetFile)) return targetFile

        // Shared with the rename dialog, which puts the same split in front of the user: the
        // number goes after the part a rename would edit, and whatever follows it is put back.
        // The stem is always a prefix of the name, which is what leaves the rest of it as the
        // extension to put back.
        val baseName = fileNameStem(name)
        val extension = name.substring(baseName.length)

        for (counter in 1..MAX_UNIQUE_FILE_ATTEMPTS) {
            targetFile = File(targetDir, "$baseName ($counter)$extension")
            if (createDestinationFile(targetFile)) return targetFile
        }

        // The only failure in this function that reaches the generic ViewModel catch, so the only
        // one that lands in Crashlytics — and `name` is the user's file. It says what failed.
        throw IOException("Cannot create unique file after $MAX_UNIQUE_FILE_ATTEMPTS attempts")
    }

    /**
     * Creates [targetFile], returning false if a file of that name already exists. A full device is
     * separated from the other create failures so that the caller can tell the user what to do
     * about it — both surface as an [IOException] from [File.createNewFile], but only one of them
     * is fixed by freeing up space.
     *
     * Neither message names the file, for the reason the transfer failure in [copyFiles] does
     * not: a private helper cannot see whether its three call chains report what they catch, so
     * the name is left out here rather than trusted to stay out of a report. Neither cause does
     * either — see [scrubbed].
     */
    private fun createDestinationFile(targetFile: File): Boolean =
        try {
            targetFile.createNewFile()
        } catch (e: IOException) {
            if (e.isNoSpaceLeft()) {
                throw InsufficientStorageException("Not enough disk space", e.scrubbed())
            }
            throw DestinationNotWritableException("Cannot create file", e.scrubbed())
        }

    fun searchFilesStreaming(
        rootPath: String,
        query: String,
        allowedRoots: List<String>,
        filters: SearchFilters = SearchFilters(),
        maxResults: Int = Int.MAX_VALUE
    ): Flow<FileItem> = flow {
        val rootFile = File(rootPath)
        if (!isWithinAllowedRoots(rootFile, allowedRoots)) {
            return@flow
        }

        var emittedCount = 0

        suspend fun searchIn(dir: File) {
            if (emittedCount >= maxResults) return

            // Names rather than the File[] `listFiles()` builds on top of them, for the reason
            // `forEachChild` carries — this walk recurses, and every level's array stays reachable
            // until its subtree is done. Its own loop rather than that helper because the filters
            // below `continue`, and the result cap returns out of the walk entirely.
            //
            // Duplicates are dropped by name, not by absolute path: two children of one directory
            // are distinct exactly when their names are. Sized past the 0.75 load factor so a large
            // directory does not rehash on its last insert.
            val names = dir.list() ?: return
            val seenNames = HashSet<String>(names.size * 4 / 3 + 1)

            for (name in names) {
                currentCoroutineContext().ensureActive()

                if (emittedCount >= maxResults) return
                if (!seenNames.add(name)) continue
                if (name.startsWith(".") && !filters.includeHidden) continue

                val file = File(dir, name)
                if (file.isSymlink()) continue

                if (name.contains(query, ignoreCase = true)) {
                    // Build the FileItem at most once. Folders ignore the type filter; files
                    // ignore it only when no types are selected (see SearchFilters.matchesType).
                    val item = when {
                        file.isDirectory ->
                            if (filters.itemKind == SearchItemKind.FILES) null else FileItem.from(file)

                        filters.itemKind == SearchItemKind.FOLDERS -> null

                        else -> FileItem.from(file).takeIf { filters.matchesType(it) }
                    }
                    if (item != null) {
                        emit(item)
                        emittedCount++
                    }
                }

                if (file.isDirectory) {
                    searchIn(file)
                }
            }
        }

        searchIn(rootFile)
    }.flowOn(Dispatchers.IO)

    fun compressFiles(
        sources: List<FileItem>,
        targetDir: String,
        zipName: String,
        allowedRoots: List<String>
    ): Flow<CompressProgress> = flow {
        val targetFolder = File(targetDir)
        if (!isWithinAllowedRoots(targetFolder, allowedRoots)) {
            throw SecurityException("Target directory is outside allowed storage paths")
        }

        // Tallied before the archive is created rather than after it: both walks recurse over the
        // whole selection, and a StackOverflowError on a deep tree or an OOM on a large one would
        // otherwise land above the catch that deletes the archive — leaving a zero-byte .zip in the
        // user's folder and skipping the finally's notifyFilesMutated() with it. The create below
        // is the last statement outside the try and leaves nothing behind when it fails, so from
        // the archive's first existence onwards every failure is guarded.
        val totalBytes = sources.sumOf { File(it.path).totalSize() }
        val totalFiles = sources.sumOf { File(it.path).totalFileCount() }

        val zipFile = getUniqueTargetFile(targetFolder, zipName)
        var compressedBytes = 0L
        var compressedFiles = 0
        var skippedFiles = 0
        // See the identical counter in [copyFiles]: a directory that could not be listed raises
        // nothing, so this is the only trace it leaves.
        var unreadableDirectories = 0
        // Only the first: one int says which errno the set has to account for, and keeping a
        // count per errno would be a histogram of the user's own storage failures for no extra
        // answer.
        var skippedErrno: Int? = null

        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
                suspend fun addToZip(file: File, basePath: String) {
                    currentCoroutineContext().ensureActive()

                    if (file.isSymlink()) {
                        return
                    }

                    val entryName = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"

                    if (file.isDirectory) {
                        zipOut.putNextEntry(ZipEntry("$entryName/"))
                        zipOut.closeEntry()
                        file.forEachChild(onListingFailed = { unreadableDirectories++ }) { child ->
                            addToZip(child, entryName)
                        }
                    } else {
                        // A file the listing named but that cannot be opened is skipped rather
                        // than failing the archive. `Android/data` and `Android/obb` on a
                        // removable volume are the case that reaches users: scoped storage lets
                        // `list()` name their entries and then denies the open, so a whole
                        // `Android/` selection used to end with a deleted archive over a
                        // `.nomedia` nobody asked for. A source deleted between the selection and
                        // this walk is indistinguishable from that and wants the same handling.
                        //
                        // libcore turns every failure of `open(2)` into a FileNotFoundException,
                        // so the type cannot say which one this is and [isStorageUnavailable]
                        // reads the errno off the cause. A failure once the stream is open is a
                        // different matter and still fails the archive.
                        //
                        // Opened before the entry is started, so a source that cannot be read
                        // leaves no zero-byte entry standing for it in the archive.
                        val input = try {
                            file.inputStream()
                        } catch (e: FileNotFoundException) {
                            // Rethrown rather than skipped when the errno says the volume went
                            // away rather than this one file: the catch below wraps it, deletes
                            // the archive and reports the failure, which is what must happen
                            // instead of skipping every remaining file and calling it a success.
                            // Everything else is this one file's problem and is stepped over.
                            if (e.isStorageUnavailable()) throw e
                            if (skippedErrno == null) skippedErrno = e.errnoOrNull()
                            skippedFiles++
                            return
                        }

                        input.use { stream ->
                            zipOut.putNextEntry(ZipEntry(entryName))
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (stream.read(buffer).also { bytes = it } >= 0) {
                                zipOut.write(buffer, 0, bytes)
                                compressedBytes += bytes
                                emit(
                                    CompressProgress(
                                        currentFile = file.name,
                                        compressedFiles = compressedFiles,
                                        totalFiles = totalFiles,
                                        compressedBytes = compressedBytes,
                                        totalBytes = totalBytes,
                                        skippedFiles = skippedFiles,
                                        skippedErrno = skippedErrno,
                                        unreadableDirectories = unreadableDirectories
                                    )
                                )
                            }
                        }
                        zipOut.closeEntry()
                        compressedFiles++
                    }
                }

                // Not deduplicated, unlike each directory below them: two sources sharing a name
                // is a caller bug, and the ZipException that follows is meant to be seen. The UI
                // cannot produce one — a selection is a subset of one `listFiles` result, which
                // has already dropped repeated names.
                sources.forEach { source ->
                    addToZip(File(source.path), "")
                }

                // The transfer's probe, for the same reason and on the same terms: an archive whose
                // missing entries are a denied `Android/data` is a partial success, and one whose
                // missing entries are a volume that left is a failure, and only the volume itself
                // can tell the two apart.
                // Only when nothing made it in, for the reason the transfer's copy of this gives:
                // an archive holding everything that could be read is a partial success, and
                // deleting it would take the one copy the user has of whatever was still readable.
                if (compressedFiles == 0 &&
                    (skippedFiles > 0 || unreadableDirectories > 0) &&
                    !storageStillAnswers(sources.map { it.path }, allowedRoots)
                ) {
                    throw FileTransferIOException("Source storage is no longer available")
                }
            }
        } catch (e: Throwable) {
            zipFile.delete()

            if (e.isNoSpaceLeft()) {
                throw InsufficientStorageException("Not enough disk space", e.scrubbed())
            }

            // An IOException while the archive is being written is environmental for the same
            // reasons the copy's byte transfer is: removable storage unmounted mid-archive
            // (EIO/ENODEV), a failing flash chip, a source that vanished. ZipException is left
            // alone — it names a malformed entry this code produced, which is a bug worth seeing.
            // Everything else — cancellation included — is rethrown unchanged so callers keep
            // seeing its own type.
            // Already classified, and by this same block's own rules — rethrown as it is so the
            // message it was given survives instead of being replaced by this one.
            if (e is FileTransferIOException) throw e

            if (e is IOException && e !is ZipException) {
                throw FileTransferIOException("Failed to compress files", e.scrubbed())
            }

            throw e
        } finally {
            // getUniqueTargetFile above already created the archive, so from here on the tree has
            // changed. A rejected target or an unwritable destination throws before that and leaves
            // the cached sizes alone.
            notifyFilesMutated()
        }

        emit(
            CompressProgress(
                currentFile = "",
                compressedFiles = compressedFiles,
                totalFiles = totalFiles,
                compressedBytes = compressedBytes,
                totalBytes = totalBytes,
                isComplete = true,
                outputPath = zipFile.absolutePath,
                skippedFiles = skippedFiles,
                skippedErrno = skippedErrno,
                unreadableDirectories = unreadableDirectories
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * @param onRolledBack invoked with the absolute paths the rollback removed after a failed or
     * cancelled extraction, so the caller can undo whatever it registered for them. Each path is a
     * root: a directory this extraction created stands for everything that was under it, exactly as
     * it does for the rollback itself. Only paths whose removal fully succeeded are reported, which
     * is what a caller deleting MediaStore rows by prefix needs of its input — a row whose file is
     * still on disk would take the file with it.
     *
     * Reported from here rather than accumulated by the caller: [UncompressProgress.extractedPaths]
     * arrives in batches precisely so nothing has to hold a path per extracted file until the end,
     * and remembering them anyway just to undo them on failure would put that list back. The
     * rollback's own bookkeeping is already in memory and already collapsed to roots.
     */
    fun uncompressFile(
        zipPath: String,
        targetDir: String,
        password: String? = null,
        allowedRoots: List<String>,
        onRolledBack: suspend (List<String>) -> Unit = {}
    ): Flow<UncompressProgress> = flow {
        val targetFolder = File(targetDir)
        if (!isWithinAllowedRoots(targetFolder, allowedRoots)) {
            throw SecurityException("Target directory is outside allowed storage paths")
        }

        val targetCanonicalPath = targetFolder.canonicalPath

        ZipFile(zipPath).use { zip ->
            if (password != null) {
                zip.setPassword(password.toCharArray())
            }

            val headers: List<FileHeader> = zip.fileHeaders

            // Validate password before extracting any files
            val firstEncrypted = headers.firstOrNull { it.isEncrypted }
            if (firstEncrypted != null) {
                zip.getInputStream(firstEncrypted).use { input ->
                    val testBuffer = ByteArray(1)
                    input.read(testBuffer)
                }
            }
            val totalFiles = headers.count { !it.isDirectory }
            val totalBytes = headers.sumOf { it.uncompressedSize.coerceAtLeast(0) }

            if (totalBytes > MAX_UNCOMPRESSED_SIZE) {
                throw ZipBombException("Uncompressed size exceeds maximum allowed")
            }

            val availableSpace = StatFs(targetDir).availableBytes
            if (totalBytes > availableSpace) {
                throw InsufficientStorageException("Not enough disk space")
            }

            var extractedBytes = 0L
            var extractedFiles = 0
            // Reported to the caller in batches and started fresh after each one, rather than kept
            // until the extraction ends: one absolute path per extracted file is unbounded in the
            // size of the archive and has run small-heap devices out of memory. Reassigned rather
            // than cleared so that a batch already handed to the caller is never mutated afterwards.
            var extractedPaths = ArrayList<String>()
            // What a failure has to remove, as paths relative to the target. The shallowest
            // directory on an extracted file's path that this extraction created stands for
            // everything below it, so a nested archive costs one path per created folder rather than
            // one per file. A file whose directories were all already there has no such cover —
            // removing one of them would take the user's own files with it — and is tracked
            // individually, as is a file extracted straight into the target, which is its own cover.
            val createdPaths = LinkedHashSet<String>()
            val createdInExistingDirs = mutableListOf<String>()
            var currentTargetFile: File? = null

            /**
             * Claims the shallowest directory along [segments] that this extraction has to create,
             * so that a rollback can delete it whole, and answers whether one was found. False means
             * every directory on the way was already there and the caller has to track what it
             * creates file by file. Always asked before anything on the path is created, or every
             * directory would look like one that was already there.
             */
            fun claimCoveringDirectory(segments: List<String>): Boolean {
                val path = StringBuilder()

                for (segment in segments) {
                    if (path.isNotEmpty()) path.append(File.separatorChar)
                    path.append(segment)
                    val relativePath = path.toString()

                    if (relativePath in createdPaths) return true

                    if (!File(targetFolder, relativePath).exists()) {
                        createdPaths.add(relativePath)
                        return true
                    }
                }

                return false
            }

            try {
                for (header in headers) {
                    currentCoroutineContext().ensureActive()

                    val destFile = File(targetFolder, header.fileName)
                    val destCanonicalPath = destFile.canonicalPath

                    // Zip Slip protection: ensure the destination stays within the target
                    // directory. Naming the target itself is allowed only for a directory entry —
                    // the "./" several archivers put at the front of an archive. A file entry that
                    // resolves there ("../" followed by the target's own name) passes the canonical
                    // check and is then written under `destFile`'s lexical parent, which is the
                    // folder above the one the user chose.
                    if (!destCanonicalPath.startsWith(targetCanonicalPath + File.separator) &&
                        !(header.isDirectory && destCanonicalPath == targetCanonicalPath)
                    ) {
                        throw ZipSlipException()
                    }

                    // The rollback follows where the entry is written — under `destFile`'s own parent
                    // — rather than where its name resolves to. "photos/." canonicalises to the
                    // folder the file is created inside, so a record keyed on the resolved name
                    // would send the rollback looking for it one level too high. A name that is not
                    // already in normalized form is given no cover at all and tracked file by file,
                    // which stays correct whatever the name turns out to mean.
                    val segments = destFile.path
                        .removePrefix(targetFolder.path + File.separator)
                        .split(File.separatorChar)
                    val isNormalizedEntry =
                        destFile.path.startsWith(targetFolder.path + File.separator) &&
                            segments.none { it.isEmpty() || it == "." || it == ".." }

                    if (header.isDirectory) {
                        if (isNormalizedEntry) claimCoveringDirectory(segments)
                        destFile.mkdirs()
                    } else {
                        // Asked before the directories below are created. Empty for a file extracted
                        // straight into the target, which no directory covers.
                        val parentSegments = segments.dropLast(1).takeIf { isNormalizedEntry }
                        val coveredByDirectory =
                            parentSegments != null && claimCoveringDirectory(parentSegments)

                        val parentDir = destFile.parentFile ?: targetFolder
                        parentDir.mkdirs()
                        val targetFile = getUniqueTargetFile(parentDir, destFile.name)
                        currentTargetFile = targetFile
                        zip.getInputStream(header).use { input ->
                            targetFile.outputStream().use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytes: Int
                                while (input.read(buffer).also { bytes = it } >= 0) {
                                    output.write(buffer, 0, bytes)
                                    extractedBytes += bytes

                                    if (extractedBytes > MAX_UNCOMPRESSED_SIZE) {
                                        throw ZipBombException("Extraction exceeded maximum allowed size")
                                    }

                                    emit(
                                        UncompressProgress(
                                            currentFile = header.fileName,
                                            extractedFiles = extractedFiles,
                                            totalFiles = totalFiles,
                                            extractedBytes = extractedBytes,
                                            totalBytes = totalBytes
                                        )
                                    )
                                }
                            }
                        }
                        currentTargetFile = null

                        when {
                            // The directory claimed above takes the file with it.
                            coveredByDirectory -> Unit
                            // Extracted straight into the target, under a name getUniqueTargetFile
                            // found free, so the file is this extraction's to delete.
                            parentSegments?.isEmpty() == true -> createdPaths.add(targetFile.name)
                            else -> createdInExistingDirs.add(targetFile.absolutePath)
                        }

                        extractedPaths.add(targetFile.absolutePath)
                        extractedFiles++

                        if (extractedPaths.size >= MEDIA_PATH_BATCH_SIZE) {
                            emit(
                                UncompressProgress(
                                    currentFile = header.fileName,
                                    extractedFiles = extractedFiles,
                                    totalFiles = totalFiles,
                                    extractedBytes = extractedBytes,
                                    totalBytes = totalBytes,
                                    extractedPaths = extractedPaths
                                )
                            )
                            extractedPaths = ArrayList()
                        }
                    }
                }
            } catch (e: Throwable) {
                // Clean up partial output on any failure — cancellation, I/O error,
                // corrupt entry, zip bomb, or zip slip — so a cancelled or failed
                // extraction never leaves extracted or half-written files behind.
                // Through the repository's own recursive delete rather than the stdlib one, which
                // walks into a symlinked directory and would take its target's contents with it.
                val rolledBack = mutableListOf<String>()
                currentTargetFile?.let { if (it.delete()) rolledBack.add(it.absolutePath) }
                // Guarded on existence because the question here is not the one a delete asks.
                // A delete is satisfied by a path that already holds nothing, so [deleteTree]
                // answers true for a path this extraction claimed but never managed to create —
                // and the caller drops MediaStore rows by prefix, and a media provider unlinks the
                // file behind a row it drops, so naming a path this rollback did not remove would
                // take a file still on disk with it.
                createdInExistingDirs.forEach {
                    val created = File(it)
                    if (created.exists() && deleteTree(created)) rolledBack.add(it)
                }
                createdPaths.forEach {
                    val created = File(targetFolder, it)
                    if (created.exists() && deleteTree(created)) rolledBack.add(created.absolutePath)
                }

                // NonCancellable for the reason notifyFilesMutated is: the usual way an extraction
                // ends is the user cancelling it, and a suspending callback would then be cancelled
                // at its first suspension point, leaving the caller's view of the removed files as
                // it was. Guarded because a callback that threw here would replace the failure
                // being reported — a cancellation included — with its own.
                withContext(NonCancellable) { runCatching { onRolledBack(rolledBack) } }

                // The pre-flight space check above can still be overtaken by another app filling
                // the volume mid-extraction, so report a full device as such rather than as a
                // generic extraction failure.
                //
                // The one classify site that can be handed an already-wrapped exception:
                // getUniqueTargetFile runs inside the try above, so a create failure arrives here
                // as the wrapper createDestinationFile threw, whose cause [scrubbed] has already
                // severed from its ErrnoException. This check therefore answers false where it
                // once answered true, and the fall-through rethrows that wrapper unchanged — which
                // is the InsufficientStorageException this branch would have built anyway. A
                // classifier that needs the errno has to run above the wrap, not here.
                if (e.isNoSpaceLeft()) {
                    throw InsufficientStorageException("Not enough disk space", e.scrubbed())
                }

                throw e
            } finally {
                // Only the extraction loop writes. The pre-flight checks above — password
                // validation, the zip-bomb ceiling, the free-space test — reject before anything
                // lands on disk, and a wrong password is retried, so invalidating there would cost
                // a full re-walk of every location per attempt.
                notifyFilesMutated()
            }

            emit(
                UncompressProgress(
                    currentFile = "",
                    extractedFiles = extractedFiles,
                    totalFiles = totalFiles,
                    extractedBytes = extractedBytes,
                    totalBytes = totalBytes,
                    isComplete = true,
                    extractedPaths = extractedPaths
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getZipInfo(zipPath: String): ZipInfo = withContext(Dispatchers.IO) {
        ZipFile(zipPath).use { zip ->
            ZipInfo(
                entryCount = zip.fileHeaders.size,
                isEncrypted = zip.isEncrypted
            )
        }
    }

    /**
     * Counts everything under [items], directories included, for callers deciding whether an
     * operation is large enough to run behind a progress dialog — a tree of thousands of
     * directories takes just as long to walk as one of thousands of files. The leaf-only
     * [totalFileCount] stays the denominator of the progress the dialog then reports.
     *
     * Only the count is kept: a list of the paths themselves is unbounded in the size of the tree
     * and stays alive for as long as the caller holds it.
     */
    suspend fun totalNodeCount(items: List<FileItem>): Int = withContext(Dispatchers.IO) {
        items.sumOf { File(it.path).totalNodeCount() }
    }

    private fun File.totalNodeCount(): Int {
        if (isSymlink()) return 0
        if (!isDirectory) return 1
        var total = 1
        forEachChild { total += it.totalNodeCount() }
        return total
    }

    suspend fun totalSize(items: List<FileItem>): Long = withContext(Dispatchers.IO) {
        items.sumOf { File(it.path).totalSize() }
    }

    private fun File.totalSize(): Long {
        if (isSymlink()) return 0L
        if (!isDirectory) return length()
        var total = 0L
        forEachChild { total += it.totalSize() }
        return total
    }

    private fun File.totalFileCount(): Int {
        if (isSymlink()) return 0
        if (!isDirectory) return 1
        var total = 0
        forEachChild { total += it.totalFileCount() }
        return total
    }

    /**
     * Runs [action] over each of this directory's entries, invoking [onListingFailed] and nothing
     * else when the directory cannot be read.
     *
     * Walks `list()`'s names and builds one child [File] per step rather than taking the array
     * `listFiles()` returns. `listFiles()` calls `list()` and then materialises an N-element `File[]`
     * on top of it, every element carrying its own joined absolute path, so a directory costs
     * several times the names alone — and in a recursive walk each level's array stays reachable
     * until its whole subtree is done, so the peak is that cost summed down the deepest path. Here
     * a level holds its names and a single child, which is the difference between walking a large
     * tree and running a small-heap device out of memory partway through.
     *
     * A name the listing returns twice is visited once, the way [listFiles] drops it for the rows on
     * screen. Both names resolve to the same file, so a second visit is never a second file — it
     * copies one source into a collision-renamed duplicate, counts a successful delete as a failure,
     * fails a whole archive on the entry name ZipOutputStream rejects, and inflates every total
     * computed here. Deduplicating in the one walker all of those share is what keeps a walk and the
     * totals taken over it from disagreeing. [dedupeInPlace] owns the set that needs, which is what
     * leaves the paragraph above still true: a level holds its names and a single child, not a set
     * as well.
     */
    private inline fun File.forEachChild(
        onListingFailed: () -> Unit = {},
        action: (File) -> Unit
    ) {
        // `list()` answers null for a directory it could not read, and every walk before this
        // parameter existed treated that as an empty directory — which is how a volume that goes
        // away mid-walk produces a clean success over a subtree nothing ever saw. Callers that
        // report on what they covered pass this; the rest keep the old shape.
        val names = list() ?: return onListingFailed()
        // Only the first `count` entries are names to visit; see dedupeInPlace for the rest.
        val count = dedupeInPlace(names)

        for (index in 0 until count) {
            action(File(this, names[index]))
        }
    }

    /**
     * Compacts [names] so that its first `n` entries are its distinct names in the order the
     * listing gave them, and returns that `n`. **Entries from `n` onwards are stale and must not be
     * read** — this rewrites the array rather than allocating a smaller one, which `list()` makes
     * safe by handing back a fresh array on every call that nothing else holds.
     *
     * A function of its own rather than a loop inside [forEachChild] so that the set it needs is
     * unreachable by the time that walker recurses. [forEachChild] is `inline` and its recursion
     * happens inside the caller's frame, so a set held there would stay live across every child and
     * sum down the deepest path — the retention [forEachChild] avoids `listFiles()` to escape, and
     * roughly half of it back. Here one set exists at a time, whatever the depth.
     */
    private fun dedupeInPlace(names: Array<String>): Int {
        // Sized past the 0.75 load factor so a large directory does not rehash on its last insert.
        val seenNames = HashSet<String>(names.size * 4 / 3 + 1)
        var count = 0

        for (index in names.indices) {
            val name = names[index]
            if (seenNames.add(name)) names[count++] = name
        }

        return count
    }

    /**
     * Returns [names] with repeated entries dropped, keeping the first of each and their order, and
     * leaving the input untouched. [dedupeInPlace] compacts the caller's own array instead, so this
     * exists to exercise the rule every recursive walk in this class applies to a directory listing.
     */
    @VisibleForTesting
    fun distinctNames(names: Array<String>): List<String> {
        val compacted = names.copyOf()
        return compacted.take(dedupeInPlace(compacted))
    }

    /**
     * Copies [source]'s modification time, skipping timestamps that are not usable: `lastModified`
     * returns 0 when it is unknown (missing file or I/O error) and can be negative for pre-epoch
     * timestamps found on removable media or in files restored from archives, which
     * `setLastModified` rejects with an IllegalArgumentException.
     */
    private fun File.copyLastModifiedFrom(source: File) {
        val timestamp = source.lastModified()
        if (timestamp > 0) setLastModified(timestamp)
    }

    private fun isPathTooLong(name: String, parentPath: String): Boolean {
        val nameBytes = name.toByteArray(Charsets.UTF_8).size
        val fullPathBytes = (parentPath + File.separator + name).toByteArray(Charsets.UTF_8).size
        return nameBytes > MAX_NAME_LENGTH || fullPathBytes > MAX_PATH_LENGTH
    }

    /**
     * Whether the volume holding [paths] still answers, probed once and only after a walk has
     * already lost something — a file it could not open, or a directory it could not list.
     *
     * The errno test the walks use first ([isStorageUnavailable]) cannot see every way a volume
     * leaves: `File.list()` returning null raises nothing at all, and whether an open failure even
     * carries an errno is a property of the platform. What is left is to ask the volume directly,
     * and a stat that fails is the answer no errno was needed for.
     *
     * The stat itself is [storageAnswersAt], which is a function of its own so that a JVM test can
     * state its answer.
     *
     * Roots rather than the files themselves: a source that was deleted mid-walk would fail its own
     * stat on a perfectly healthy volume, which is the case this must not report as storage loss.
     */
    private fun storageStillAnswers(paths: List<String>, allowedRoots: List<String>): Boolean {
        val roots = paths.mapNotNullTo(mutableSetOf()) { path ->
            val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return@mapNotNullTo null
            // The deepest match, not the first: one root nested inside another is the volume the
            // path is actually on, and the outer one can answer for storage that is no longer
            // there.
            allowedRoots
                .filter { root ->
                    val canonicalRoot = runCatching { File(root).canonicalPath }.getOrNull()
                    canonicalRoot != null &&
                        (canonical == canonicalRoot || canonical.startsWith(canonicalRoot + File.separator))
                }
                .maxByOrNull { it.length }
        }

        // Empty when no source sits under any allowed root, which the sources are never checked
        // for — only the target is. Answering true there is the deliberate direction: a probe that
        // cannot tell which volume to ask must not be what turns a partial success into a failure.
        return roots.all { root -> storageAnswersAt(root) }
    }

    private fun isWithinAllowedRoots(target: File, allowedRoots: List<String>): Boolean {
        return try {
            val canonicalTarget = target.canonicalPath
            val canonicalAllowedRoots = allowedRoots.map { File(it).canonicalPath }
            canonicalAllowedRoots.any { canonicalAllowed ->
                canonicalTarget.startsWith(canonicalAllowed + File.separator) ||
                    canonicalTarget == canonicalAllowed
            }
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val MAX_UNCOMPRESSED_SIZE = 10L * 1024 * 1024 * 1024 // 10 GB
        private const val MAX_NAME_LENGTH = 255
        private const val MAX_PATH_LENGTH = 4096
        private const val MAX_UNIQUE_FILE_ATTEMPTS = 1000

        /**
         * How many created paths [copyFiles] and [uncompressFile] hold before handing them to the
         * caller and starting a new batch. Large enough that a transfer of a few hundred files still
         * reports once, at the end, and small enough that the batch stays a rounding error against
         * the file data the same transfer moves.
         */
        private const val MEDIA_PATH_BATCH_SIZE = 500
    }
}

@Immutable
data class CopyProgress(
    val currentFile: String,
    val copiedFiles: Int,
    val totalFiles: Int,
    val copiedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    /**
     * True on a move (`deleteAfter`) when one or more sources could not be deleted after a
     * successful copy (e.g. a read-only source volume). The copy succeeded, but the originals
     * remain on disk, so the caller must not report an unqualified success nor notify MediaStore
     * that the sources were removed.
     */
    val sourceDeleteFailed: Boolean = false,
    /**
     * Absolute paths of the files actually created at the destination since the previous emission
     * that carried any — recursive, with the collision-resolved names assigned by
     * [FileRepository.getUniqueTargetFile]. Directories are omitted (no media to index). Files from
     * a transfer that threw before completing are not omitted — they reach the caller through
     * `onPartialTransfer` instead, which is the only hand-off a failure has.
     *
     * Arrives in batches while the transfer runs, not only on the final [isComplete] emission, so
     * the caller has to scan every emission's paths rather than the last one's: holding a path per
     * copied file until the end is unbounded in the size of the tree and has run devices out of
     * heap. A batch reported before the transfer failed names files that are on disk, and scanning
     * them is correct whether or not the rest of the transfer completes.
     */
    val createdPaths: List<String> = emptyList(),
    /**
     * Absolute paths of the source files actually removed during a move since the previous emission
     * that carried any — recursive, batched exactly like [createdPaths] and handled the same way by
     * the caller, which notifies MediaStore that these are gone. Empty for a copy and for any
     * source whose deletion failed.
     *
     * [sourceDeleteFailed] is sticky and carried on every batch, so it suppresses the batch that
     * first reports the failure and every batch after it. Batches handed over before that point
     * have already been reported, and stay accurate: each of their paths names a file whose
     * deletion did succeed.
     */
    val deletedSourcePaths: List<String> = emptyList(),
    /**
     * Move sources that already held nothing when the transfer reached them. The move's
     * postcondition is met for these, but they are kept out of [deletedSourcePaths] because this
     * app did not remove them and cannot say what occupies the path now — the caller scans them
     * rather than reporting them deleted. See
     * [com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome].
     */
    val absentSourcePaths: List<String> = emptyList(),
    /**
     * How many files the walk named but could not open, and therefore did not transfer. Counted
     * towards [totalFiles], which is tallied from the same listing, so the two together say how
     * much of the selection made it across.
     *
     * Non-zero is a partial success and not a failure: everything else is at the destination, and
     * on a move the originals of the skipped files are still where they were — the delete is
     * reached only by a file that was copied first. The caller is expected to say so rather than
     * report the whole transfer as failed.
     */
    val skippedFiles: Int = 0,
    /** The errno behind the first skip, or null. See [CompressProgress.skippedErrno]. */
    val skippedErrno: Int? = null,
    /**
     * How many directories the walk named but could not list. Their contents were never seen, so
     * they are in no total and in no other count — [skippedFiles] is leaf files, and
     * `totalFileCount` went blind on the same directories, which is exactly why a subtree lost this
     * way used to come out as a clean success. Non-zero means the same thing to a caller as a
     * non-zero [skippedFiles]: not everything made it.
     */
    val unreadableDirectories: Int = 0
)

@Immutable
data class CompressProgress(
    val currentFile: String,
    val compressedFiles: Int,
    val totalFiles: Int,
    val compressedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val outputPath: String? = null,
    /**
     * How many files the walk named but could not open, and therefore left out of the archive.
     * Counted towards [totalFiles], which is tallied from the same listing, so the two together say
     * how much of the selection made it in. Non-zero is a partial success and not a failure: the
     * archive is complete for everything else, and the caller is expected to say so rather than
     * report the whole operation as failed.
     */
    val skippedFiles: Int = 0,
    /**
     * The errno behind the first skip, or null when the platform attached none. Reported with the
     * partial-success analytics event so that the set
     * [com.mauriciotogneri.fileexplorer.data.util.isStorageUnavailable] fails on can be checked
     * against what devices actually produce — an errno nobody listed is the one way this rule goes
     * wrong quietly, by stepping over a volume that has gone away.
     */
    val skippedErrno: Int? = null,
    /**
     * How many directories the walk named but could not list. Their contents were never seen, so
     * they are in no total and in no other count — [skippedFiles] is leaf files, and
     * `totalFileCount` went blind on the same directories, which is exactly why a subtree lost this
     * way used to come out as a clean success. Non-zero means the same thing to a caller as a
     * non-zero [skippedFiles]: not everything made it.
     */
    val unreadableDirectories: Int = 0
)

@Immutable
data class UncompressProgress(
    val currentFile: String,
    val extractedFiles: Int,
    val totalFiles: Int,
    val extractedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    /**
     * Absolute paths of the files extracted since the previous emission that carried any, with the
     * collision-resolved names assigned by [FileRepository.getUniqueTargetFile]. Directories are
     * omitted (no media to index).
     *
     * Arrives in batches while the extraction runs, not only on the final [isComplete] emission, so
     * the caller has to scan every emission's paths rather than the last one's: holding a path per
     * extracted file until the end is unbounded in the size of the archive and has run devices out
     * of heap. A failed extraction then deletes what it created, including files whose batch was
     * already reported — those come back through [FileRepository.uncompressFile]'s `onRolledBack`,
     * so the caller can drop what it registered for them rather than remember every path in case.
     */
    val extractedPaths: List<String> = emptyList()
)

@Immutable
data class DeleteProgress(
    val currentFile: String,
    val deletedFiles: Int,
    val totalFiles: Int,
    val failedFiles: Int = 0,
    /**
     * True when a directory or symlink in the tree could not be removed even though every leaf
     * file may have been deleted (e.g. a read-only parent volume). Tracked separately from
     * [failedFiles] — which counts only files, to match [totalFiles] — so the caller can report
     * an incomplete deletion without inflating the progress fraction. Populated only on the final
     * [isComplete] emission.
     */
    val structuralDeleteFailed: Boolean = false,
    /**
     * The selected roots this walk emptied, having unlinked at least one node under each, and the
     * ones that already held nothing. Only the first may be reported to MediaStore as deleted; see
     * [com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome]. Roots rather than nodes, so the
     * two lists stay bounded by the selection rather than by the tree. Populated only on the final
     * [isComplete] emission.
     *
     * A root that failed is in neither: it still holds something, so nothing may be said about it.
     */
    val removedRootPaths: List<String> = emptyList(),
    /** See [removedRootPaths]. */
    val absentRootPaths: List<String> = emptyList(),
    /**
     * The errno behind the first failure, [ERRNO_UNKNOWN] where there was one but no errno came
     * with it, and null where nothing failed. Populated only on the final [isComplete] emission,
     * and read through [com.mauriciotogneri.fileexplorer.data.util.deleteFailureFor] to decide what
     * to tell the user.
     */
    val failureErrno: Int? = null,
    val isComplete: Boolean = false
)

/**
 * The outcome of [FileRepository.delete].
 *
 * Carries the errno rather than the [DeleteFailure] it classifies to, because the caller reports
 * both: the classification decides the message, and the raw errno goes on the analytics event so
 * that a cause the classification lumps into `errno_other` can still be identified from the field.
 */
@Immutable
data class DeleteResult(
    /**
     * The selected roots this call cleared, having unlinked at least one node under each. Only
     * these may be reported to MediaStore as deleted; see
     * [com.mauriciotogneri.fileexplorer.data.util.RemoveOutcome].
     */
    val removedPaths: List<String> = emptyList(),
    /**
     * The selected roots that already held nothing. The user's request is met for these, so they
     * count towards [clearedCount] and raise no error — but this app did not remove them and
     * cannot say what occupies the path now, so the caller scans them instead of reporting them
     * deleted.
     */
    val alreadyAbsentPaths: List<String> = emptyList(),
    /** How many selected roots were not cleared. */
    val failedCount: Int = 0,
    /**
     * The errno behind the first failure, [ERRNO_UNKNOWN] where one failed without an errno, and
     * null where nothing failed. Read through
     * [com.mauriciotogneri.fileexplorer.data.util.deleteFailureFor] to decide what to tell the user.
     */
    val failureErrno: Int? = null
) {
    val success: Boolean get() = failedCount == 0

    /** Selected roots that now hold nothing, however they got that way. */
    val clearedCount: Int get() = removedPaths.size + alreadyAbsentPaths.size
}

data class ZipInfo(
    val entryCount: Int,
    val isEncrypted: Boolean
)

data class RenameResult(
    val oldPath: String,
    val newPath: String,
    val isCaseOnlyRename: Boolean = false
)

class ZipSlipException : Exception("ZIP entry contains path traversal")

class ZipBombException(message: String) : Exception(message)

class InsufficientStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Thrown when the destination file cannot be created because the OS rejects the write
 * (e.g. EPERM on removable/scoped-storage volumes that pass [File.canWrite] but still deny
 * the actual create). This is an environmental condition, not an app bug.
 */
class DestinationNotWritableException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Thrown when a copy, move or compression fails with an I/O error while the bytes are being
 * written, after the destination file was successfully created (e.g. EIO when removable storage is
 * unmounted mid-transfer, a failing flash chip, or a source disappears). This is an environmental
 * condition, not an app bug.
 */
class FileTransferIOException(message: String, cause: Throwable? = null) :
    IOException(message, cause)
