package com.mauriciotogneri.fileexplorer.data.util

import android.media.MediaMetadataRetriever
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import java.io.File

object AudioMetadataExtractor {

    fun extract(file: File): AudioMetadata? {
        if (!file.exists() || !file.canRead()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            AudioMetadata(
                duration = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }.getOrNull(),
                artist = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                album = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                title = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                genre = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                year = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                bitrate = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()?.let { it / 1000 }
                }.getOrNull(),
                trackNumber = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                discNumber = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                composer = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                albumArtist = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                writer = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull(),
                sampleRate = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                }.getOrNull(),
                bitDepth = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                }.getOrNull(),
                channels = null,
                isCompilation = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                        ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                }.getOrNull(),
                recordingDate = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        ?.trim()?.takeIf { it.isNotBlank() }
                }.getOrNull()
            )
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_audio_metadata", "audio")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

}
