package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil

@Immutable
data class Favorite(
    val path: String,
    val name: String,
    override val isDirectory: Boolean,
    val mimeType: String,
    val favoritedTimestamp: Long
) : FileTypeInfo {
    override val isImage: Boolean get() = MimeTypeUtil.isImage(mimeType)
    override val isVideo: Boolean get() = MimeTypeUtil.isVideo(mimeType)
    override val isAudio: Boolean get() = MimeTypeUtil.isAudio(mimeType)
    override val isPdf: Boolean get() = MimeTypeUtil.isPdf(mimeType)
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

    val hasImageThumbnailSupport: Boolean get() = MimeTypeUtil.hasNativeThumbnailSupport(mimeType, name)

    val hasThumbnailSupport: Boolean
        get() = hasImageThumbnailSupport || isPdf || isVideo || isApk || isAudio || isEpub || isSvg
}
