package com.mauriciotogneri.fileexplorer.data.util

import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

class AudioThumbnailFetcher(
    private val file: File,
    private val options: Options,
    diskCache: DiskCache?
) : Fetcher {

    // The embedded picture is stored as the file carries it, so one entry covers every size.
    private val thumbnailCache = ThumbnailDiskCache(diskCache, options, FILE_TYPE, file, variesWithSize = false)

    override suspend fun fetch(): FetchResult? {
        // The embedded picture is stored verbatim, in whatever format the file carries, so the
        // decoder detects it here exactly as it does on the extraction path below.
        thumbnailCache.read(mimeType = null)?.let { return it }

        return try {
            extractAlbumArt()
        } catch (e: Exception) {
            // MediaMetadataRetriever throws for corrupted, unsupported, or
            // inaccessible audio files. These are expected, unactionable
            // conditions and not worth reporting.
            if (!isUnreadableAudio(e)) {
                ErrorReporter.warning(e.scrubbed(), "extract_audio_thumbnail", FILE_TYPE)
            }
            null
        }
    }

    private fun extractAlbumArt(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val embeddedPicture = retriever.embeddedPicture ?: return null

            val buffer = Buffer()
            buffer.write(embeddedPicture)

            // A copy, because writing consumes the buffer and Coil still has to decode it.
            thumbnailCache.write(buffer.copy())

            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.exists() || !data.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isAudio(MimeTypeUtil.getMimeType(data))) {
                return null
            }
            return AudioThumbnailFetcher(data, options, imageLoader.diskCache)
        }
    }
}

private const val FILE_TYPE = ThumbnailFileType.AUDIO
