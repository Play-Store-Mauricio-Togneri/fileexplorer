package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoMetadata(
    val duration: Long?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitrate: Int?,
    val rotation: Int?,
    val colorStandard: VideoColorStandard?,
    val colorTransfer: VideoColorTransfer?,
    val audioSampleRate: Int?,
    val audioBitDepth: Int?,
    val title: String?,
    val dateRecorded: String?,
    val latitude: Double?,
    val longitude: Double?,
    val author: String?
)

enum class VideoColorStandard {
    BT601,
    BT709,
    BT2020
}

enum class VideoColorTransfer {
    SDR,
    ST2084,
    HLG
}
