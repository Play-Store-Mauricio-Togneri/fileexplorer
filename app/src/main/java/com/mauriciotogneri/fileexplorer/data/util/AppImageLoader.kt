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
    private var loaders: Loaders? = null

    private class Loaders(
        val thumbnails: ImageLoader,
        val viewer: ImageLoader
    )

    /**
     * Loader for list and grid thumbnails. Deliberately omits the animated decoders
     * (ImageDecoderDecoder/GifDecoder): a GIF or animated WebP thumbnail then decodes to a
     * single static first frame via BitmapFactory, which Coil can keep as a hardware bitmap
     * off the Java heap. Animating them instead buffers each whole encoded file plus per-frame
     * software bitmaps on-heap for every visible and prefetched row, which can exhaust memory
     * and crash with OutOfMemoryError.
     */
    fun thumbnails(context: Context): ImageLoader = loaders(context).thumbnails

    /**
     * Loader for the full-screen image viewer, where animation is wanted. Includes the animated
     * decoder so GIFs and animated WebPs play. Only ever loads viewable image files, so it needs
     * none of the media/document thumbnail fetchers.
     */
    fun viewer(context: Context): ImageLoader = loaders(context).viewer

    private fun loaders(context: Context): Loaders {
        return loaders ?: synchronized(this) {
            loaders ?: build(context.applicationContext).also { loaders = it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun build(context: Context): Loaders {
        // Shared across both loaders. A single DiskCache instance is required: two loaders
        // writing the same directory would corrupt each other. Sharing the memory cache and the
        // fetch dispatcher keeps one global budget instead of doubling each.
        //
        // Sharing the memory cache is safe only while the two loaders never resolve the same file
        // to the same pixel size (thumbnails 120/400/layout-bound; viewer 4096). The cache key
        // encodes size but not which loader produced the entry, so at an equal size the viewer
        // could be served a static first frame instead of animating. Keep their sizes disjoint.
        val memoryCache = MemoryCache.Builder(context)
            .maxSizePercent(0.15)
            .build()
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
            .maxSizeBytes(50L * 1024 * 1024)
            .build()
        // Thumbnail fetchers (video/audio/pdf/apk/epub) rely on scarce native resources such as
        // the media-codec HAL and PdfRenderer. Coil defaults fetching to Dispatchers.IO (up to 64
        // threads), which lets dozens of MediaMetadataRetriever instances hammer the media server
        // at once and starves its finalizer (crash: "MediaMetadataRetriever.finalize() timed
        // out"). One shared, capped dispatcher keeps the process-wide native subsystems healthy.
        // Plain-image decoding is unaffected — it runs on the decoderDispatcher.
        val fetcherDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_THUMBNAILS)

        fun base(): ImageLoader.Builder =
            ImageLoader.Builder(context)
                .fetcherDispatcher(fetcherDispatcher)
                .memoryCache { memoryCache }
                .diskCache { diskCache }

        val thumbnails = base()
            .components {
                add(PdfThumbnailFetcher.Factory())
                add(VideoThumbnailFetcher.Factory())
                add(ApkThumbnailFetcher.Factory())
                add(AudioThumbnailFetcher.Factory())
                add(EpubThumbnailFetcher.Factory())
                add(SvgDecoder.Factory())
                // No animated decoder: thumbnails render a static first frame (see thumbnails()).
            }
            .build()

        val viewer = base()
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        return Loaders(thumbnails, viewer)
    }

    // Hardware media decoders support only a few concurrent instances; 4 keeps
    // thumbnail grids filling quickly without saturating the native subsystems.
    private const val MAX_CONCURRENT_THUMBNAILS = 4
}
