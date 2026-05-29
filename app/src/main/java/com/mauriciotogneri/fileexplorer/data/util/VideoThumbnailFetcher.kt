package com.mauriciotogneri.fileexplorer.data.util

import android.graphics.Bitmap
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

class VideoThumbnailFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            extractVideoThumbnail()
        } catch (e: Exception) {
            // MediaMetadataRetriever throws for corrupted, unsupported, or
            // inaccessible video files. These are expected, unactionable
            // conditions and not worth reporting.
            if (!isUnreadableVideo(e)) {
                ErrorReporter.warning(e, "extract_video_thumbnail", "video")
            }
            null
        }
    }

    private fun extractVideoThumbnail(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null

            val buffer = Buffer()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())
            bitmap.recycle()

            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        } finally {
            retriever.release()
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.exists() || !data.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isVideo(MimeTypeUtil.getMimeType(data))) {
                return null
            }
            return VideoThumbnailFetcher(data, options)
        }
    }
}
