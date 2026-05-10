package com.mauriciotogneri.fileexplorer.data.util

import android.media.MediaMetadataRetriever
import android.os.Build
import com.mauriciotogneri.fileexplorer.data.model.VideoColorStandard
import com.mauriciotogneri.fileexplorer.data.model.VideoColorTransfer
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import java.io.File

object VideoMetadataExtractor {

    fun extract(file: File): VideoMetadata? {
        if (!file.exists() || !file.canRead()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val (latitude, longitude) = parseLocation(location)

            VideoMetadata(
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull(),
                frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull(),
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.let { it / 1000 },
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull(),
                colorStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
                        ?.toIntOrNull()?.let { parseColorStandard(it) }
                } else null,
                colorTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                        ?.toIntOrNull()?.let { parseColorTransfer(it) }
                } else null,
                audioSampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                } else null,
                audioBitDepth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        ?.toIntOrNull()
                } else null,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.trim()?.takeIf { it.isNotBlank() },
                dateRecorded = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ?.trim()?.takeIf { it.isNotBlank() },
                latitude = latitude,
                longitude = longitude,
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    ?.trim()?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun parseLocation(location: String?): Pair<Double?, Double?> {
        if (location.isNullOrBlank()) return Pair(null, null)
        val regex = Regex("([+-]?\\d+\\.\\d+)([+-]\\d+\\.\\d+)")
        val match = regex.find(location) ?: return Pair(null, null)
        val lat = match.groupValues.getOrNull(1)?.toDoubleOrNull()
        val lon = match.groupValues.getOrNull(2)?.toDoubleOrNull()
        return Pair(lat, lon)
    }

    private fun parseColorStandard(value: Int): VideoColorStandard? {
        return when (value) {
            1 -> VideoColorStandard.BT709
            2 -> VideoColorStandard.BT601
            6 -> VideoColorStandard.BT2020
            else -> null
        }
    }

    private fun parseColorTransfer(value: Int): VideoColorTransfer? {
        return when (value) {
            3 -> VideoColorTransfer.SDR
            6 -> VideoColorTransfer.ST2084
            7 -> VideoColorTransfer.HLG
            else -> null
        }
    }
}
