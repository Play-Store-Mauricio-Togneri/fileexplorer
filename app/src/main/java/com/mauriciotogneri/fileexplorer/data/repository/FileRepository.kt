package com.mauriciotogneri.fileexplorer.data.repository

import android.os.Build
import android.os.StatFs
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.util.isNoSpaceLeft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.IOException
import java.util.Locale
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

open class FileRepository {

    /**
     * Lists a directory's entries, hidden ones optionally filtered out, duplicates dropped
     * (first occurrence wins) and sorted by [sortMode].
     *
     * Built in a single pass into one list rather than a chain of filter/map/distinct/sort steps.
     * A directory with hundreds of thousands of entries is the app's largest allocation by far,
     * and each intermediate collection multiplies the peak — the transient spike, not the result,
     * is what runs the heap out.
     */
    open suspend fun listFiles(
        path: String,
        showHidden: Boolean,
        sortMode: SortMode
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val entries: Array<File?> = File(path).listFiles() ?: return@withContext emptyList()
        val items = ArrayList<FileItem>(entries.size)
        // Sized past the 0.75 load factor: HashSet(n) is guaranteed to rehash on the nth insert,
        // and for a large directory that doubling holds two tables at once.
        val seenPaths = HashSet<String>(entries.size * 4 / 3 + 1)

        for (index in entries.indices) {
            val entry = entries[index] ?: continue
            // Drop each File as it is consumed so the array's entries can be collected while the
            // pass runs, instead of staying alive alongside every FileItem built from them.
            entries[index] = null

            if (!showHidden && entry.name.startsWith(".")) continue
            if (!seenPaths.add(entry.absolutePath)) continue

            items.add(FileItem.from(entry))
        }

        sortInPlace(items, sortMode)
        items
    }

    /**
     * Counts a directory's direct children (hidden entries included), or null if [path] cannot be
     * read. Intentionally runs on the caller's dispatcher (no internal withContext) so the caller
     * can bound concurrency with a limited dispatcher; must be called off the main thread.
     */
    open suspend fun countChildren(path: String): Int? = File(path).list()?.size

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

            newFolder.mkdir()
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

        if (isCaseOnlyRename) {
            renameCaseOnly(sourceFile, targetFile)
        } else {
            renameRegular(sourceFile, targetFile)
        }
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

    suspend fun delete(files: List<FileItem>): Boolean = withContext(Dispatchers.IO) {
        files.all { deleteRecursive(File(it.path)) }
    }

    private fun deleteRecursive(file: File): Boolean {
        var allSucceeded = true
        if (file.isDirectory && !file.isSymlink()) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursive(child)) {
                    allSucceeded = false
                }
            }
        }
        return file.delete() && allSucceeded
    }

    fun deleteWithProgress(files: List<FileItem>): Flow<DeleteProgress> = flow {
        val totalFiles = files.sumOf { File(it.path).totalFileCount() }
        var deletedFiles = 0
        var failedFiles = 0
        var structuralDeleteFailed = false

        suspend fun deleteRecursiveWithProgress(file: File) {
            currentCoroutineContext().ensureActive()

            val isSymlink = file.isSymlink()
            val isDirectory = file.isDirectory && !isSymlink

            if (isDirectory) {
                file.listFiles()?.forEach { child ->
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

            val deleted = file.delete()

            // Only leaf files contribute to the progress totals, matching `totalFiles`
            // (computed via the leaf-only `totalFileCount`). Directories and symlinks are
            // still deleted above, just not counted — otherwise the numerator could exceed
            // the denominator and the partial-success toast would over-report failures.
            if (!isDirectory && !isSymlink) {
                if (deleted) {
                    deletedFiles++
                } else {
                    failedFiles++
                }
            } else if (!deleted) {
                // A directory or symlink that could not be removed (e.g. a read-only parent).
                // Tracked apart from the leaf-file counts so the caller can still tell the tree
                // was not fully deleted without distorting the progress fraction.
                structuralDeleteFailed = true
            }
        }

        files.forEach { fileItem ->
            deleteRecursiveWithProgress(File(fileItem.path))
        }

        emit(
            DeleteProgress(
                currentFile = "",
                deletedFiles = deletedFiles,
                totalFiles = totalFiles,
                failedFiles = failedFiles,
                structuralDeleteFailed = structuralDeleteFailed,
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    fun copyFiles(
        sources: List<FileItem>,
        targetDir: String,
        deleteAfter: Boolean,
        allowedRoots: List<String>
    ): Flow<CopyProgress> = flow {
        val targetFolder = File(targetDir)
        if (!isWithinAllowedRoots(targetFolder, allowedRoots)) {
            throw SecurityException("Target directory is outside allowed storage paths")
        }
        val totalBytes = sources.sumOf { File(it.path).totalSize() }
        val totalFiles = sources.sumOf { File(it.path).totalFileCount() }
        var copiedBytes = 0L
        var copiedFiles = 0
        var sourceDeleteFailed = false
        val createdPaths = mutableListOf<String>()
        val deletedSourcePaths = mutableListOf<String>()

        suspend fun copyRecursive(source: File, targetParent: File) {
            currentCoroutineContext().ensureActive()

            if (source.isSymlink()) {
                if (deleteAfter && !source.delete()) sourceDeleteFailed = true
                return
            }

            if (source.isDirectory) {
                val newDir = File(targetParent, source.name)
                newDir.mkdirs()
                source.listFiles()?.forEach { child ->
                    copyRecursive(child, newDir)
                }
                newDir.copyLastModifiedFrom(source)
                if (deleteAfter && !source.delete()) sourceDeleteFailed = true
            } else {
                val targetFile = getUniqueTargetFile(targetParent, source.name)
                try {
                    source.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } >= 0) {
                                output.write(buffer, 0, bytes)
                                copiedBytes += bytes
                                emit(
                                    CopyProgress(
                                        currentFile = source.name,
                                        copiedFiles = copiedFiles,
                                        totalFiles = totalFiles,
                                        copiedBytes = copiedBytes,
                                        totalBytes = totalBytes
                                    )
                                )
                            }
                        }
                    }
                } catch (e: IOException) {
                    // An IOException once the streams are open (the target file was already
                    // created) is environmental, not an app bug: removable storage unmounted
                    // mid-copy (EIO/ENODEV), a failing flash chip, the source vanished, etc.
                    // CancellationException is not an IOException, so cancellation still escapes.
                    if (e.isNoSpaceLeft()) {
                        throw InsufficientStorageException("Not enough disk space", e)
                    }
                    throw FileTransferIOException("Failed to copy file: ${source.name}", e)
                }
                targetFile.copyLastModifiedFrom(source)
                copiedFiles++
                createdPaths.add(targetFile.absolutePath)
                if (deleteAfter) {
                    if (source.delete()) {
                        deletedSourcePaths.add(source.absolutePath)
                    } else {
                        sourceDeleteFailed = true
                    }
                }
            }
        }

        sources.forEach { source ->
            copyRecursive(File(source.path), targetFolder)
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
                deletedSourcePaths = deletedSourcePaths
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun getUniqueTargetFile(targetDir: File, name: String): File {
        var targetFile = File(targetDir, name)
        if (createDestinationFile(targetFile)) return targetFile

        val baseName = name.substringBeforeLast(".", name)
        val extension = name.substringAfterLast(".", "").let {
            if (it == name) "" else ".$it"
        }

        for (counter in 1..MAX_UNIQUE_FILE_ATTEMPTS) {
            targetFile = File(targetDir, "$baseName ($counter)$extension")
            if (createDestinationFile(targetFile)) return targetFile
        }

        throw IOException("Cannot create unique file after $MAX_UNIQUE_FILE_ATTEMPTS attempts: $name")
    }

    /**
     * Creates [targetFile], returning false if a file of that name already exists. A full device is
     * separated from the other create failures so that the caller can tell the user what to do
     * about it — both surface as an [IOException] from [File.createNewFile], but only one of them
     * is fixed by freeing up space.
     */
    private fun createDestinationFile(targetFile: File): Boolean =
        try {
            targetFile.createNewFile()
        } catch (e: IOException) {
            if (e.isNoSpaceLeft()) {
                throw InsufficientStorageException("Not enough disk space", e)
            }
            throw DestinationNotWritableException("Cannot create file: ${targetFile.name}", e)
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

            val files = dir.listFiles()?.distinctBy { it.absolutePath } ?: return
            for (file in files) {
                currentCoroutineContext().ensureActive()

                if (emittedCount >= maxResults) return
                if (file.name.startsWith(".") && !filters.includeHidden) continue
                if (file.isSymlink()) continue

                if (file.name.contains(query, ignoreCase = true)) {
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
        val zipFile = getUniqueTargetFile(targetFolder, zipName)
        val totalBytes = sources.sumOf { File(it.path).totalSize() }
        val totalFiles = sources.sumOf { File(it.path).totalFileCount() }
        var compressedBytes = 0L
        var compressedFiles = 0

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
                        file.listFiles()?.forEach { child ->
                            addToZip(child, entryName)
                        }
                    } else {
                        zipOut.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } >= 0) {
                                zipOut.write(buffer, 0, bytes)
                                compressedBytes += bytes
                                emit(
                                    CompressProgress(
                                        currentFile = file.name,
                                        compressedFiles = compressedFiles,
                                        totalFiles = totalFiles,
                                        compressedBytes = compressedBytes,
                                        totalBytes = totalBytes
                                    )
                                )
                            }
                        }
                        zipOut.closeEntry()
                        compressedFiles++
                    }
                }

                sources.forEach { source ->
                    addToZip(File(source.path), "")
                }
            }
        } catch (e: Exception) {
            zipFile.delete()

            if (e.isNoSpaceLeft()) {
                throw InsufficientStorageException("Not enough disk space", e)
            }

            throw e
        }

        emit(
            CompressProgress(
                currentFile = "",
                compressedFiles = compressedFiles,
                totalFiles = totalFiles,
                compressedBytes = compressedBytes,
                totalBytes = totalBytes,
                isComplete = true,
                outputPath = zipFile.absolutePath
            )
        )
    }.flowOn(Dispatchers.IO)

    fun uncompressFile(
        zipPath: String,
        targetDir: String,
        password: String? = null,
        allowedRoots: List<String>
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
            val extractedPaths = mutableListOf<String>()
            var currentTargetFile: File? = null

            try {
                for (header in headers) {
                    currentCoroutineContext().ensureActive()

                    val destFile = File(targetFolder, header.fileName)

                    // Zip Slip protection: ensure the destination stays within the target directory
                    if (!destFile.canonicalPath.startsWith(targetCanonicalPath + File.separator) &&
                        destFile.canonicalPath != targetCanonicalPath
                    ) {
                        throw ZipSlipException()
                    }

                    if (header.isDirectory) {
                        destFile.mkdirs()
                    } else {
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
                        extractedPaths.add(targetFile.absolutePath)
                        extractedFiles++
                    }
                }
            } catch (e: Throwable) {
                // Clean up partial output on any failure — cancellation, I/O error,
                // corrupt entry, zip bomb, or zip slip — so a cancelled or failed
                // extraction never leaves extracted or half-written files behind.
                currentTargetFile?.delete()
                extractedPaths.forEach { File(it).delete() }

                // The pre-flight space check above can still be overtaken by another app filling
                // the volume mid-extraction, so report a full device as such rather than as a
                // generic extraction failure.
                if (e.isNoSpaceLeft()) {
                    throw InsufficientStorageException("Not enough disk space", e)
                }

                throw e
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
        return if (isDirectory) 1 + (listFiles()?.sumOf { it.totalNodeCount() } ?: 0) else 1
    }

    suspend fun totalSize(items: List<FileItem>): Long = withContext(Dispatchers.IO) {
        items.sumOf { File(it.path).totalSize() }
    }

    private fun File.totalSize(): Long {
        if (isSymlink()) return 0L
        return if (isDirectory) listFiles()?.sumOf { it.totalSize() } ?: 0L else length()
    }

    private fun File.totalFileCount(): Int {
        if (isSymlink()) return 0
        return if (isDirectory) listFiles()?.sumOf { it.totalFileCount() } ?: 0 else 1
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

    private fun File.isSymlink(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // A name that cannot be represented as a Path is reported as a regular file rather than
            // guessed at: the canonical-path comparison below re-encodes the name lossily, so it
            // would answer true for a plain file, and callers treat symlinks as entries to skip —
            // copy, compress and search would drop it and still report success. Every java.io call
            // on such a name fails, so callers surface a real error instead.
            val path = toPathOrNull() ?: return false
            return Files.isSymbolicLink(path)
        }

        // Pre-O, compare the canonical path against the parent's canonical path plus this entry's
        // name.
        return try {
            parentFile?.let { parent ->
                canonicalPath != File(parent.canonicalFile, name).path
            } ?: false
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Returns this file as a [Path], or null when its name cannot be represented as one.
     * [File.toPath] re-encodes the name with the platform charset and rejects names whose bytes are
     * not valid UTF-8 — common in downloaded files whose names were truncated mid-character, which
     * surface as unpaired surrogates. Callers must degrade to the `java.io` API, which tolerates
     * them, instead of propagating the unchecked [InvalidPathException].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun File.toPathOrNull(): Path? = try {
        toPath()
    } catch (_: InvalidPathException) {
        null
    }

    private fun isPathTooLong(name: String, parentPath: String): Boolean {
        val nameBytes = name.toByteArray(Charsets.UTF_8).size
        val fullPathBytes = (parentPath + File.separator + name).toByteArray(Charsets.UTF_8).size
        return nameBytes > MAX_NAME_LENGTH || fullPathBytes > MAX_PATH_LENGTH
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
    }
}

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
     * Absolute paths of the files actually created at the destination — recursive, with the
     * collision-resolved names assigned by [FileRepository.getUniqueTargetFile]. Populated only on
     * the final [isComplete] emission; the caller scans exactly these into MediaStore. Directories
     * are omitted (no media to index), as are files from a transfer that threw before completing.
     */
    val createdPaths: List<String> = emptyList(),
    /**
     * Absolute paths of the source files actually removed during a move — recursive. Populated only
     * on the final [isComplete] emission; the caller notifies MediaStore that exactly these are
     * gone. Empty for a copy and for any source whose deletion failed.
     */
    val deletedSourcePaths: List<String> = emptyList()
)

data class CompressProgress(
    val currentFile: String,
    val compressedFiles: Int,
    val totalFiles: Int,
    val compressedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val outputPath: String? = null
)

@Immutable
data class UncompressProgress(
    val currentFile: String,
    val extractedFiles: Int,
    val totalFiles: Int,
    val extractedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val extractedPaths: List<String> = emptyList()
)

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
    val isComplete: Boolean = false
)

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
 * Thrown when a copy/move fails with an I/O error during the byte transfer itself, after the
 * destination file was successfully created (e.g. EIO when removable storage is unmounted
 * mid-copy, a failing flash chip, or the source disappears). This is an environmental condition,
 * not an app bug.
 */
class FileTransferIOException(message: String, cause: Throwable? = null) :
    IOException(message, cause)
