package com.mauriciotogneri.fileexplorer.data.util

import android.media.MediaMetadataRetriever
import com.mauriciotogneri.fileexplorer.data.model.AudioChannels
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import java.io.File

object AudioMetadataExtractor {

    fun extract(file: File): AudioMetadata? {
        if (!file.exists() || !file.canRead()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            AudioMetadata(
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.trim()?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.trim()?.takeIf { it.isNotBlank() },
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.trim()?.takeIf { it.isNotBlank() },
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    ?.trim()?.takeIf { it.isNotBlank() },
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.trim()?.takeIf { it.isNotBlank() },
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.let { it / 1000 },
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.trim()?.takeIf { it.isNotBlank() },
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.trim()?.takeIf { it.isNotBlank() },
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    ?.trim()?.takeIf { it.isNotBlank() },
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.trim()?.takeIf { it.isNotBlank() },
                writer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                    ?.trim()?.takeIf { it.isNotBlank() },
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull(),
                bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull(),
                channels = null, // Channel count not reliably available via MediaMetadataRetriever
                isCompilation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                    ?.let { it == "1" || it.equals("true", ignoreCase = true) },
                recordingDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
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

}
