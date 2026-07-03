package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

object AppImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            // Thumbnail fetchers (video/audio/pdf/apk/epub) rely on scarce native
            // resources such as the media-codec HAL and PdfRenderer. Coil defaults
            // fetching to Dispatchers.IO (up to 64 threads), which lets dozens of
            // MediaMetadataRetriever instances hammer the media server at once and
            // starves its finalizer (crash: "MediaMetadataRetriever.finalize() timed
            // out"). Cap concurrent fetches to keep the native subsystems healthy.
            // Plain-image decoding is unaffected — it runs on the decoderDispatcher.
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_THUMBNAILS))
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .components {
                add(PdfThumbnailFetcher.Factory())
                add(VideoThumbnailFetcher.Factory())
                add(ApkThumbnailFetcher.Factory())
                add(AudioThumbnailFetcher.Factory())
                add(EpubThumbnailFetcher.Factory())
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // Hardware media decoders support only a few concurrent instances; 4 keeps
    // thumbnail grids filling quickly without saturating the native subsystems.
    private const val MAX_CONCURRENT_THUMBNAILS = 4
}
