package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

@Immutable
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val createdTime: Long,
    val mimeType: String,
    val childCount: Int? = null
) {
    val extension: String
        get() = name.substringAfterLast('.', "")
            .takeIf { it != name && it.length <= 4 }
            ?.uppercase()
            ?: ""

    val formattedSize: String
        get() = FileSizeFormatter.format(size)

    val isImage: Boolean get() = MimeTypeUtil.isImage(mimeType)
    val isPdf: Boolean get() = MimeTypeUtil.isPdf(mimeType)
    val isAudio: Boolean get() = MimeTypeUtil.isAudio(mimeType)
    val isVideo: Boolean get() = MimeTypeUtil.isVideo(mimeType)

    fun exists(): Boolean = File(path).exists()

    val parentPath: String
        get() = File(path).parent ?: ""

    companion object {
        fun from(file: File): FileItem {
            val createdTime = try {
                val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                attrs.creationTime().toMillis()
            } catch (e: Exception) {
                file.lastModified()
            }

            return FileItem(
                path = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                createdTime = createdTime,
                mimeType = if (file.isDirectory) "" else MimeTypeUtil.getMimeType(file),
                childCount = if (file.isDirectory) file.listFiles()?.size else null
            )
        }
    }
}
