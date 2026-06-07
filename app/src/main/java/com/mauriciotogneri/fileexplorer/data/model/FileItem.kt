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
    override val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val createdTime: Long,
    val mimeType: String,
    val childCount: Int? = null
) : FileTypeInfo {
    val formattedSize: String
        get() = FileSizeFormatter.format(size)

    override val isImage: Boolean get() = MimeTypeUtil.isImage(mimeType)
    val hasImageThumbnailSupport: Boolean get() = MimeTypeUtil.hasNativeThumbnailSupport(mimeType, name)
    val isViewableImage: Boolean get() = MimeTypeUtil.isViewableImage(mimeType, name, Build.VERSION.SDK_INT)
    override val isPdf: Boolean get() = MimeTypeUtil.isPdf(mimeType)
    override val isAudio: Boolean get() = MimeTypeUtil.isAudio(mimeType)
    override val isVideo: Boolean get() = MimeTypeUtil.isVideo(mimeType)
    override val isApk: Boolean get() = MimeTypeUtil.isApk(mimeType)
    override val isZip: Boolean get() = MimeTypeUtil.isZip(mimeType)
    override val isArchive: Boolean get() = MimeTypeUtil.isArchive(mimeType)
    override val isOfficeDocument: Boolean get() = MimeTypeUtil.isOfficeDocument(mimeType)
    override val isEpub: Boolean get() = MimeTypeUtil.isEpub(mimeType)
    override val isFont: Boolean get() = MimeTypeUtil.isFont(mimeType) || MimeTypeUtil.isFontByExtension(name)
    override val isSvg: Boolean get() = MimeTypeUtil.isSvg(mimeType) || MimeTypeUtil.isSvgByExtension(name)
    override val isSqlite: Boolean get() = MimeTypeUtil.isSqlite(mimeType) || MimeTypeUtil.isSqliteByExtension(name)
    override val isVCard: Boolean get() = MimeTypeUtil.isVCard(mimeType) || MimeTypeUtil.isVCardByExtension(name)
    override val isICalendar: Boolean get() = MimeTypeUtil.isICalendar(mimeType) || MimeTypeUtil.isICalendarByExtension(name)
    override val isCsv: Boolean get() = MimeTypeUtil.isCsv(mimeType) || MimeTypeUtil.isCsvByExtension(name)
    override val isText: Boolean get() = MimeTypeUtil.isText(mimeType) || MimeTypeUtil.isTextByExtension(name)

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
                mimeType = if (file.isDirectory) "" else MimeTypeUtil.getMimeType(file)
            )
        }
    }
}
