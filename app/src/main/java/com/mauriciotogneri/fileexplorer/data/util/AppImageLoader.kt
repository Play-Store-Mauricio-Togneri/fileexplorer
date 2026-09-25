package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.addLastModifiedToFileCacheKey
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.Path.Companion.toOkioPath

object AppImageLoader {

    @Volatile
    private var loaders: Loaders? = null

    private class Loaders(
        val thumbnails: ImageLoader,
        val viewer: ImageLoader
    )

    /**
     * Loader for list and grid thumbnails. Registers no animated decoder, so a GIF or animated WebP
     * decodes to a single static first frame via the platform decoders Coil registers by default,
     * which it can keep as a hardware bitmap off the Java heap. Animating them instead buffers each
     * whole encoded file plus per-frame software bitmaps on-heap for every visible and prefetched
     * row, which can exhaust memory and crash with OutOfMemoryError.
     *
     * Leaving the animated decoder out is only enough because [build] also turns off Coil's
     * ServiceLoader discovery — see the comment there.
     */
    fun thumbnails(context: Context): ImageLoader = loaders(context).thumbnails

    /**
     * Loader for the full-screen image viewer, where animation is wanted. Includes the animated
     * decoder so GIFs and animated WebPs play. Only ever loads viewable image files, so it needs
     * none of the media/document thumbnail fetchers.
     */
    fun viewer(context: Context): ImageLoader = loaders(context).viewer

    /**
     * The cache holding extracted thumbnails, for [evictThumbnail] to drop entries from when their
     * file is deleted. Exposed without a Context because deleting a file is not a place to build an
     * image loader, and null until something has loaded a thumbnail in this process and built one.
     *
     * Entries written by earlier runs are on disk from process start, so a delete before that first
     * load leaves one behind for eviction to reclaim later rather than dropping it. In practice a
     * file can only reach the cache by being displayed, which builds the loader well before its row
     * offers anything to delete.
     */
    val thumbnailDiskCache: DiskCache? get() = loaders?.thumbnails?.diskCache

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
        //
        // Coil never fills this cache itself here — it writes it only from its network fetcher,
        // which is a separate artifact this app does not depend on, and every request is a local
        // file. What populates it is ThumbnailDiskCache, which the thumbnail fetchers below use to
        // keep an extracted video frame, PDF page, APK icon, album art or EPUB cover across process
        // restarts instead of re-extracting it whenever the memory cache is cleared. Plain images
        // are not stored: decoding one is far cheaper than a second copy of it.
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache").toOkioPath())
            .maxSizeBytes(50L * 1024 * 1024)
            .build()
        // Memory caches must NOT be shared. An entry is a decoded result, and for the same file the
        // two loaders want different results: thumbnails a static first frame, the viewer an
        // animated drawable. Coil keys memory-cache entries by the file's URI alone — these
        // requests set no transformations, so the requested size is absent from the key — so a
        // shared cache would hand the viewer the thumbnail loader's frozen first frame whenever a
        // GIF's pixel size is at or below its thumbnail size, and it would show static instead of
        // animating.
        // Separate caches keep the two loaders' decoded results distinct. The viewer displays one
        // image at a time, so its cache can be small.
        val thumbnailsMemoryCache = MemoryCache.Builder()
            .maxSizePercent(context, 0.15)
            .build()
        val viewerMemoryCache = MemoryCache.Builder()
            .maxSizePercent(context, 0.05)
            .build()
        // Thumbnail fetchers (video/audio/pdf/apk/epub) rely on scarce native resources such as
        // the media-codec HAL and PdfRenderer. Coil defaults fetching to Dispatchers.IO (up to 64
        // threads), which lets dozens of MediaMetadataRetriever instances hammer the media server
        // at once and starves its finalizer (crash: "MediaMetadataRetriever.finalize() timed
        // out"). One shared, capped dispatcher keeps the process-wide native subsystems healthy.
        // Plain-image decoding is unaffected — it runs on the decoderCoroutineContext.
        val fetcherDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_THUMBNAILS)

        fun base(memoryCache: MemoryCache): ImageLoader.Builder =
            ImageLoader.Builder(context)
                // Every optional Coil artifact ships a ServiceLoader entry that registers its
                // decoders into each loader built, whether that loader wants them or not. Left on,
                // coil-gif's animated decoder lands in the thumbnails loader below, which exists to
                // decode a single static frame (see [thumbnails]) — and no ordering of the
                // components block reliably keeps it away from bytes a fetcher produced in memory.
                // Both loaders name every decoder they need, so discovery has nothing to add here.
                .serviceLoaderEnabled(false)
                .fetcherCoroutineContext(fetcherDispatcher)
                .memoryCache { memoryCache }
                .diskCache { diskCache }

        val thumbnails = base(thumbnailsMemoryCache)
            // Coil runs its interceptor chain on Dispatchers.Main.immediate, and building the
            // default memory cache key for a file appends its last-modified time — a stat syscall
            // on the main thread for every row, inside the list's measure pass. When storage is
            // congested that stalls the frame and can ANR. Keying by the file's URI alone removes
            // the syscall; call sites that already know a file's timestamp (see
            // FileItem.thumbnailCacheKey) pass an explicit memoryCacheKey instead, so a file edited
            // in place still gets a fresh thumbnail. Stated rather than left to the default —
            // which happens to agree — because that pair of decisions is why every request carries
            // a memoryCacheKey of its own. Enabled on the viewer below: it loads one image per
            // screen rather than one per row, so the single stat is not worth the risk of showing
            // a stale full-resolution image when a file is replaced at the same path.
            .addLastModifiedToFileCacheKey(false)
            .components {
                add(PdfThumbnailFetcher.Factory())
                add(VideoThumbnailFetcher.Factory())
                add(ApkThumbnailFetcher.Factory())
                add(AudioThumbnailFetcher.Factory())
                add(EpubThumbnailFetcher.Factory())
                add(SvgDecoder.Factory())
                // No animated decoder: thumbnails render a static first frame (see [thumbnails]).
                // What decodes them is the pair Coil registers by default — ImageDecoder from API
                // 29, BitmapFactory below it and for the in-memory buffers the fetchers above
                // return — which share one lock and so stay within a single decode budget.
            }
            .build()

        val viewer = base(viewerMemoryCache)
            // Coil 3 no longer adds the timestamp by default; the viewer wants it (see above).
            .addLastModifiedToFileCacheKey(true)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
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
