package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil

@Immutable
data class RecentFile(
    val path: String,
    val name: String,
    val mimeType: String,
    val lastOpenedTimestamp: Long
) {
    val isImage: Boolean get() = MimeTypeUtil.isImage(mimeType)
    val isVideo: Boolean get() = MimeTypeUtil.isVideo(mimeType)
    val isAudio: Boolean get() = MimeTypeUtil.isAudio(mimeType)
    val isPdf: Boolean get() = MimeTypeUtil.isPdf(mimeType)
    val isApk: Boolean get() = MimeTypeUtil.isApk(mimeType)
    val isEpub: Boolean get() = MimeTypeUtil.isEpub(mimeType)
    val isSvg: Boolean get() = MimeTypeUtil.isSvg(mimeType) || MimeTypeUtil.isSvgByExtension(name)

    val hasImageThumbnailSupport: Boolean get() = MimeTypeUtil.hasNativeThumbnailSupport(mimeType, name)

    val hasThumbnailSupport: Boolean
        get() = hasImageThumbnailSupport || isPdf || isVideo || isApk || isAudio || isEpub || isSvg
}
