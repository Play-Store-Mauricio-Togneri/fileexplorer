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
        // A single shared DiskCache instance is required: two loaders writing the same directory
        // would corrupt each other. The disk cache stores encoded source bytes, which are identical
        // regardless of loader, so sharing it is correct (and the shared, capped fetch dispatcher
        // below keeps one global budget for the native thumbnail subsystems).
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
            .maxSizeBytes(50L * 1024 * 1024)
            .build()
        // Memory caches must NOT be shared. An entry is a decoded result, and for the same file the
        // two loaders want different results: thumbnails a static first frame (no animated decoder),
        // the viewer an animated drawable. Coil keys memory-cache entries by file path only — these
        // requests set no transformations, so the requested size is absent from the key — so a shared
        // cache would hand the viewer the thumbnail loader's frozen first frame whenever a GIF's
        // pixel size is at or below its thumbnail size, and it would show static instead of animating.
        // Separate caches keep the two loaders' decoded results distinct. The viewer displays one
        // image at a time, so its cache can be small.
        val thumbnailsMemoryCache = MemoryCache.Builder(context)
            .maxSizePercent(0.15)
            .build()
        val viewerMemoryCache = MemoryCache.Builder(context)
            .maxSizePercent(0.05)
            .build()
        // Thumbnail fetchers (video/audio/pdf/apk/epub) rely on scarce native resources such as
        // the media-codec HAL and PdfRenderer. Coil defaults fetching to Dispatchers.IO (up to 64
        // threads), which lets dozens of MediaMetadataRetriever instances hammer the media server
        // at once and starves its finalizer (crash: "MediaMetadataRetriever.finalize() timed
        // out"). One shared, capped dispatcher keeps the process-wide native subsystems healthy.
        // Plain-image decoding is unaffected — it runs on the decoderDispatcher.
        val fetcherDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_THUMBNAILS)

        fun base(memoryCache: MemoryCache): ImageLoader.Builder =
            ImageLoader.Builder(context)
                .fetcherDispatcher(fetcherDispatcher)
                .memoryCache { memoryCache }
                .diskCache { diskCache }

        val thumbnails = base(thumbnailsMemoryCache)
            // Coil runs its interceptor chain on Dispatchers.Main.immediate, and building the
            // default memory cache key for a File appends File.lastModified() — a stat syscall on
            // the main thread for every row, inside the list's measure pass. When storage is
            // congested that stalls the frame and can ANR. Keying by path alone removes the
            // syscall; call sites that already know a file's timestamp (see
            // FileItem.thumbnailCacheKey) pass an explicit memoryCacheKey instead, so a file edited
            // in place still gets a fresh thumbnail. Left enabled on the viewer below: it loads one
            // image per screen rather than one per row, so the single stat is not worth the risk of
            // showing a stale full-resolution image when a file is replaced at the same path.
            .addLastModifiedToFileCacheKey(false)
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

        val viewer = base(viewerMemoryCache)
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
