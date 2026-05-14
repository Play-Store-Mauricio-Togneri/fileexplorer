package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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

    suspend fun rename(file: FileItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(file.path)
        val targetFile = File(sourceFile.parent, newName)
        if (targetFile.exists()) {
            false
        } else {
            sourceFile.renameTo(targetFile)
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

    suspend fun searchFiles(
        rootPath: String,
        query: String,
        recursive: Boolean,
        showHidden: Boolean
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()

        fun searchIn(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (!showHidden && file.name.startsWith(".")) return@forEach

                if (file.name.contains(query, ignoreCase = true)) {
                    results.add(FileItem.from(file))
                }
                if (recursive && file.isDirectory) {
                    searchIn(file)
                }
            }
        }

        searchIn(File(rootPath))
        results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

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
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes else 0f
}

data class CompressProgress(
    val currentFile: String,
    val compressedFiles: Int,
    val totalFiles: Int,
    val compressedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) compressedBytes.toFloat() / totalBytes else 0f
}
