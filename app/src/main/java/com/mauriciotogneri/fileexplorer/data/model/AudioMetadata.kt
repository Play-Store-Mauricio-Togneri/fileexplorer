package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class AudioMetadata(
    val duration: Long?,
    val artist: String?,
    val album: String?,
    val title: String?,
    val genre: String?,
    val year: String?,
    val bitrate: Int?,
    val trackNumber: String?,
    val discNumber: String?,
    val composer: String?,
    val albumArtist: String?,
    val writer: String?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: AudioChannels?,
    val isCompilation: Boolean?,
    val recordingDate: String?
)

enum class AudioChannels {
    MONO,
    STEREO
}
