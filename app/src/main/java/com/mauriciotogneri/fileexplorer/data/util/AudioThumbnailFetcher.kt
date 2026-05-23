package com.mauriciotogneri.fileexplorer.data.util

import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

class AudioThumbnailFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            extractAlbumArt()
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_audio_thumbnail", "audio")
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

            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                ErrorReporter.warning(e, "release_media_retriever", "audio")
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
            return AudioThumbnailFetcher(data, options)
        }
    }
}
