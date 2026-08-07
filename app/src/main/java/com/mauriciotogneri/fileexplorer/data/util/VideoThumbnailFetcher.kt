package com.mauriciotogneri.fileexplorer.data.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
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

class VideoThumbnailFetcher(
    private val file: File,
    private val options: Options,
    diskCache: DiskCache?
) : Fetcher {

    // The frame is decoded at the size requested, so an entry only covers requests up to that size.
    private val thumbnailCache = ThumbnailDiskCache(diskCache, options, FILE_TYPE, file, variesWithSize = true)

    override suspend fun fetch(): FetchResult? {
        thumbnailCache.read(MIME_TYPE)?.let { return it }

        return try {
            extractVideoThumbnail()
        } catch (e: Exception) {
            // MediaMetadataRetriever throws for corrupted, unsupported, or
            // inaccessible video files. These are expected, unactionable
            // conditions and not worth reporting.
            if (!isUnreadableVideo(e)) {
                ErrorReporter.warning(e, "extract_video_thumbnail", FILE_TYPE)
            }
            null
        }
    }

    private fun extractVideoThumbnail(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            // Decode the frame at (roughly) thumbnail size. getFrameAtTime returns the frame at
            // the video's native resolution — a single 4K frame is ~33 MB (ARGB_8888) and several
            // fetch concurrently, spiking the heap into OutOfMemoryError. Scaling at decode time
            // (API 27+) keeps the transient bitmap small; older APIs fall back to the full frame.
            val targetWidth = options.thumbnailWidth().coerceAtLeast(1)
            val targetHeight = options.thumbnailHeight().coerceAtLeast(1)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                )
            } else {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null

            val buffer = Buffer()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())
            bitmap.recycle()

            // A copy, because writing consumes the buffer and Coil still has to decode it.
            thumbnailCache.write(buffer.copy())

            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = MIME_TYPE,
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
            if (!MimeTypeUtil.isVideo(MimeTypeUtil.getMimeType(data))) {
                return null
            }
            return VideoThumbnailFetcher(data, options, imageLoader.diskCache)
        }
    }
}

private const val FILE_TYPE = ThumbnailFileType.VIDEO
private const val MIME_TYPE = "image/jpeg"
