package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecentFile(
    val path: String,
    val name: String,
    val mimeType: String,
    val lastOpenedTimestamp: Long
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
}
