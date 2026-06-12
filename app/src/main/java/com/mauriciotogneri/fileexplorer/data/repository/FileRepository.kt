package com.mauriciotogneri.fileexplorer.data.repository

import android.os.Build
import android.os.StatFs
import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.data.model.SortMode
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
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

open class FileRepository {

    open suspend fun listFiles(
        path: String,
        showHidden: Boolean,
        sortMode: SortMode
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val files = File(path).listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.map { FileItem.from(it) }
            ?.distinctBy { it.path }
            ?: emptyList()

        sortFiles(files, sortMode)
    }

    /**
     * Counts a directory's direct children (hidden entries included), or null if [path] cannot be
     * read. Intentionally runs on the caller's dispatcher (no internal withContext) so the caller
     * can bound concurrency with a limited dispatcher; must be called off the main thread.
     */
    open suspend fun countChildren(path: String): Int? = File(path).list()?.size

    fun sortFiles(files: List<FileItem>, sortMode: SortMode): List<FileItem> {
        val folders = files.filter { it.isDirectory }
        val regularFiles = files.filter { !it.isDirectory }

        return sortByMode(folders, sortMode) + sortByMode(regularFiles, sortMode)
    }

    private fun sortByMode(files: List<FileItem>, sortMode: SortMode): List<FileItem> =
        when (sortMode) {
            SortMode.NAME_ASC -> sortByName(files, descending = false)
            SortMode.NAME_DESC -> sortByName(files, descending = true)
            SortMode.SIZE_ASC -> files.sortedBy { it.size }
            SortMode.SIZE_DESC -> files.sortedByDescending { it.size }
            SortMode.DATE_ASC -> files.sortedBy { it.lastModified }
            SortMode.DATE_DESC -> files.sortedByDescending { it.lastModified }
        }

    /**
     * Sorts by name using a decorate-sort-undecorate pass so each name is lowercased once (O(n))
     * rather than on every comparison (O(n log n)), as `compareBy { it.name.lowercase() }` would.
     * The sort stays stable, so entries with equal lowercased names keep their input order.
     */
    private fun sortByName(files: List<FileItem>, descending: Boolean): List<FileItem> {
        val decorated = files.map { it.name.lowercase(Locale.ROOT) to it }
        val ordered = if (descending) {
            decorated.sortedByDescending { it.first }
        } else {
            decorated.sortedBy { it.first }
        }
        return ordered.map { it.second }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                RenameResult(
                    oldPath = sourceFile.absolutePath,
                    newPath = targetFile.absolutePath
                )
            } catch (_: AtomicMoveNotSupportedException) {
                try {
                    Files.move(sourceFile.toPath(), targetFile.toPath())
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
        } else {
            return if (targetFile.exists()) {
                null
            } else if (sourceFile.renameTo(targetFile)) {
                RenameResult(
                    oldPath = sourceFile.absolutePath,
                    newPath = targetFile.absolutePath
                )
            } else {
                null
            }
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

        suspend fun deleteRecursiveWithProgress(file: File) {
            currentCoroutineContext().ensureActive()

            if (file.isDirectory && !file.isSymlink()) {
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

            if (file.delete()) {
                deletedFiles++
            } else {
                failedFiles++
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

        suspend fun copyRecursive(source: File, targetParent: File) {
            currentCoroutineContext().ensureActive()

            if (source.isSymlink()) {
                if (deleteAfter) source.delete()
                return
            }

            if (source.isDirectory) {
                val newDir = File(targetParent, source.name)
                newDir.mkdirs()
                source.listFiles()?.forEach { child ->
                    copyRecursive(child, newDir)
                }
                newDir.setLastModified(source.lastModified())
                if (deleteAfter) source.delete()
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
                    throw FileTransferIOException("Failed to copy file: ${source.name}", e)
                }
                targetFile.setLastModified(source.lastModified())
                copiedFiles++
                if (deleteAfter) source.delete()
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
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun getUniqueTargetFile(targetDir: File, name: String): File {
        var targetFile = File(targetDir, name)
        try {
            if (targetFile.createNewFile()) return targetFile
        } catch (e: IOException) {
            throw DestinationNotWritableException("Cannot create file: ${targetFile.name}", e)
        }

        val baseName = name.substringBeforeLast(".", name)
        val extension = name.substringAfterLast(".", "").let {
            if (it == name) "" else ".$it"
        }

        for (counter in 1..MAX_UNIQUE_FILE_ATTEMPTS) {
            targetFile = File(targetDir, "$baseName ($counter)$extension")
            try {
                if (targetFile.createNewFile()) return targetFile
            } catch (e: IOException) {
                throw DestinationNotWritableException("Cannot create file: ${targetFile.name}", e)
            }
        }

        throw IOException("Cannot create unique file after $MAX_UNIQUE_FILE_ATTEMPTS attempts: $name")
    }

    fun searchFilesStreaming(
        rootPath: String,
        query: String,
        allowedRoots: List<String>,
        filters: SearchFilters = SearchFilters(),
        maxResults: Int = Int.MAX_VALUE
    ): Flow<FileItem> = flow {
        val rootFile = File(rootPath)
        val canonicalRoot = rootFile.canonicalPath

        val canonicalAllowedRoots = allowedRoots.map { File(it).canonicalPath }
        val isWithinAllowedRoot = canonicalAllowedRoots.any { canonicalAllowed ->
            canonicalRoot.startsWith(canonicalAllowed + File.separator) ||
                canonicalRoot == canonicalAllowed
        }

        if (!isWithinAllowedRoot) {
            return@flow
        }

        var emittedCount = 0

        suspend fun searchIn(dir: File) {
            if (emittedCount >= maxResults) return

            val files = dir.listFiles()?.distinctBy { it.absolutePath } ?: return
            for (file in files) {
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
            } catch (e: ZipBombException) {
                // Clean up partially extracted files
                currentTargetFile?.delete()
                extractedPaths.forEach { File(it).delete() }
                throw e
            } catch (e: ZipSlipException) {
                // Clean up partially extracted files
                extractedPaths.forEach { File(it).delete() }
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

    suspend fun collectAllPaths(files: List<FileItem>): List<String> = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        fun collect(file: File) {
            if (file.isSymlink()) {
                return
            }
            if (file.isDirectory) {
                file.listFiles()?.forEach { collect(it) }
            }
            paths.add(file.absolutePath)
        }
        files.forEach { collect(File(it.path)) }
        paths
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

    private fun File.isSymlink(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.isSymbolicLink(toPath())
        } else {
            try {
                parentFile?.let { parent ->
                    canonicalPath != File(parent.canonicalFile, name).path
                } ?: false
            } catch (_: IOException) {
                false
            }
        }
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
    val isComplete: Boolean = false
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

class InsufficientStorageException(message: String) : Exception(message)

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
