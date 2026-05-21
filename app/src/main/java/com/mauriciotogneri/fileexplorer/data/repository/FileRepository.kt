package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.FileItem
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileRepository {

    suspend fun listFiles(
        path: String,
        showHidden: Boolean,
        sortMode: SortMode
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val files = File(path).listFiles()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.map { FileItem.from(it) }
            ?: emptyList()

        sortFiles(files, sortMode)
    }

    fun sortFiles(files: List<FileItem>, sortMode: SortMode): List<FileItem> {
        val folders = files.filter { it.isDirectory }
        val regularFiles = files.filter { !it.isDirectory }

        val comparator: Comparator<FileItem> = when (sortMode) {
            SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
            SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortMode.SIZE_ASC -> compareBy { it.size }
            SortMode.SIZE_DESC -> compareByDescending { it.size }
            SortMode.DATE_ASC -> compareBy { it.lastModified }
            SortMode.DATE_DESC -> compareByDescending { it.lastModified }
        }

        return folders.sortedWith(comparator) + regularFiles.sortedWith(comparator)
    }

    suspend fun createFolder(parentPath: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            val newFolder = File(parentPath, name)
            if (newFolder.exists()) {
                false
            } else {
                newFolder.mkdir()
            }
        }

    suspend fun rename(file: FileItem, newName: String): RenameResult? = withContext(Dispatchers.IO) {
        val sourceFile = File(file.path)
        val targetFile = File(sourceFile.parent, newName)
        if (targetFile.exists()) {
            null
        } else if (sourceFile.renameTo(targetFile)) {
            RenameResult(oldPath = file.path, newPath = targetFile.absolutePath)
        } else {
            null
        }
    }

    suspend fun delete(files: List<FileItem>): Boolean = withContext(Dispatchers.IO) {
        files.all { deleteRecursive(File(it.path)) }
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        return file.delete()
    }

    fun deleteWithProgress(files: List<FileItem>): Flow<DeleteProgress> = flow {
        val totalFiles = files.sumOf { File(it.path).totalFileCount() }
        var deletedFiles = 0
        var failedFiles = 0

        suspend fun deleteRecursiveWithProgress(file: File) {
            currentCoroutineContext().ensureActive()

            if (file.isDirectory) {
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
        deleteAfter: Boolean
    ): Flow<CopyProgress> = flow {
        val targetFolder = File(targetDir)
        val totalBytes = sources.sumOf { File(it.path).totalSize() }
        val totalFiles = sources.sumOf { File(it.path).totalFileCount() }
        var copiedBytes = 0L
        var copiedFiles = 0

        suspend fun copyRecursive(source: File, targetParent: File) {
            if (source.isDirectory) {
                val newDir = File(targetParent, source.name)
                newDir.mkdirs()
                source.listFiles()?.forEach { child ->
                    copyRecursive(child, newDir)
                }
                if (deleteAfter) source.delete()
            } else {
                val targetFile = getUniqueTargetFile(targetParent, source.name)
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
        if (!targetFile.exists()) return targetFile

        val baseName = name.substringBeforeLast(".", name)
        val extension = name.substringAfterLast(".", "").let {
            if (it == name) "" else ".$it"
        }

        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(targetDir, "$baseName ($counter)$extension")
            counter++
        }
        return targetFile
    }

    fun searchFilesStreaming(
        rootPath: String,
        query: String,
        maxResults: Int = Int.MAX_VALUE
    ): Flow<FileItem> = flow {
        var emittedCount = 0

        suspend fun searchIn(dir: File) {
            if (emittedCount >= maxResults) return

            val files = dir.listFiles() ?: return
            for (file in files) {
                if (emittedCount >= maxResults) return
                if (file.name.startsWith(".")) continue

                if (!file.isDirectory && file.name.contains(query, ignoreCase = true)) {
                    emit(FileItem.from(file))
                    emittedCount++
                }

                if (file.isDirectory) {
                    searchIn(file)
                }
            }
        }

        searchIn(File(rootPath))
    }.flowOn(Dispatchers.IO)

    fun compressFiles(
        sources: List<FileItem>,
        targetDir: String,
        zipName: String
    ): Flow<CompressProgress> = flow {
        val zipFile = getUniqueTargetFile(File(targetDir), zipName)
        val totalBytes = sources.sumOf { File(it.path).totalSize() }
        val totalFiles = sources.sumOf { File(it.path).totalFileCount() }
        var compressedBytes = 0L
        var compressedFiles = 0

        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
                suspend fun addToZip(file: File, basePath: String) {
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
        password: String? = null
    ): Flow<UncompressProgress> = flow {
        val targetFolder = File(targetDir)
        val targetCanonicalPath = targetFolder.canonicalPath

        ZipFile(zipPath).use { zip ->
            if (password != null) {
                zip.setPassword(password.toCharArray())
            }

            val headers: List<FileHeader> = zip.fileHeaders
            val totalFiles = headers.count { !it.isDirectory }
            val totalBytes = headers.sumOf { it.uncompressedSize.coerceAtLeast(0) }
            var extractedBytes = 0L
            var extractedFiles = 0
            val extractedPaths = mutableListOf<String>()

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
                    zip.getInputStream(header).use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } >= 0) {
                                output.write(buffer, 0, bytes)
                                extractedBytes += bytes
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
                    extractedPaths.add(targetFile.absolutePath)
                    extractedFiles++
                }
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

    fun collectAllPaths(files: List<FileItem>): List<String> {
        val paths = mutableListOf<String>()
        fun collect(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { collect(it) }
            }
            paths.add(file.absolutePath)
        }
        files.forEach { collect(File(it.path)) }
        return paths
    }

    private fun File.totalSize(): Long =
        if (isDirectory) listFiles()?.sumOf { it.totalSize() } ?: 0L else length()

    private fun File.totalFileCount(): Int =
        if (isDirectory) listFiles()?.sumOf { it.totalFileCount() } ?: 0 else 1

    companion object {
        private const val BUFFER_SIZE = 8192
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
    val newPath: String
)

class ZipSlipException : Exception("ZIP entry contains path traversal")
