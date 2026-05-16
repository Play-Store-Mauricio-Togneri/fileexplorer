package com.mauriciotogneri.fileexplorer.data.model

import android.os.Build
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
    val hasImageThumbnailSupport: Boolean get() = MimeTypeUtil.hasNativeThumbnailSupport(mimeType, name)
    val isPdf: Boolean get() = MimeTypeUtil.isPdf(mimeType)
    val isAudio: Boolean get() = MimeTypeUtil.isAudio(mimeType)
    val isVideo: Boolean get() = MimeTypeUtil.isVideo(mimeType)
    val isApk: Boolean get() = MimeTypeUtil.isApk(mimeType)
    val isZip: Boolean get() = MimeTypeUtil.isZip(mimeType)
    val isOfficeDocument: Boolean get() = MimeTypeUtil.isOfficeDocument(mimeType)
    val isEpub: Boolean get() = MimeTypeUtil.isEpub(mimeType)
    val isSvg: Boolean get() = MimeTypeUtil.isSvg(mimeType) || MimeTypeUtil.isSvgByExtension(name)
    val isSqlite: Boolean get() = MimeTypeUtil.isSqlite(mimeType) || MimeTypeUtil.isSqliteByExtension(name)
    val isVCard: Boolean get() = MimeTypeUtil.isVCard(mimeType) || MimeTypeUtil.isVCardByExtension(name)
    val isICalendar: Boolean get() = MimeTypeUtil.isICalendar(mimeType) || MimeTypeUtil.isICalendarByExtension(name)
    val isCsv: Boolean get() = MimeTypeUtil.isCsv(mimeType) || MimeTypeUtil.isCsvByExtension(name)

    val hasThumbnailSupport: Boolean
        get() = hasImageThumbnailSupport || isPdf || isVideo || isApk || isAudio || isEpub || isSvg

    fun exists(): Boolean = File(path).exists()

    val parentPath: String
        get() = File(path).parent ?: ""

    companion object {
        fun from(file: File): FileItem {
            val createdTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                    attrs.creationTime().toMillis()
                } catch (e: Exception) {
                    file.lastModified()
                }
            } else {
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
