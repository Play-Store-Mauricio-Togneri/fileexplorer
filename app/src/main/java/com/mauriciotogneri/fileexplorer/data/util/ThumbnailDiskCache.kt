package com.mauriciotogneri.fileexplorer.data.util

import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import okio.Buffer
import java.io.File

/**
 * Persists the thumbnails produced by the media and document fetchers.
 *
 * Coil only ever writes its own disk cache from [coil.fetch.HttpUriFetcher], so a custom fetcher's
 * result lives in the memory cache and nowhere else: every process restart, and every
 * `onTrimMemory` that clears the cache, throws the work away. For an audio file that only costs a
 * second copy of the embedded album art, but a video frame costs a MediaMetadataRetriever decode,
 * a rescale and a JPEG encode — throttled to a handful at a time (see AppImageLoader) — which is
 * long enough to watch a grid of videos fill in again row by row.
 *
 * The bytes cached here are the encoded ones each fetcher already hands to Coil, so a hit skips
 * extraction entirely and decodes straight from the cache file.
 *
 * A file gets **one** entry however many sizes the app asks for it at. The size an entry covers is
 * recorded alongside it rather than folded into its key, and a larger cached thumbnail satisfies a
 * smaller request — Coil downsamples it while decoding. That keeps the list row, the home card and
 * the item info screen sharing a single copy instead of storing three, and it means [evictThumbnail]
 * can name a file's entry without knowing which sizes were ever requested for it.
 */
@OptIn(ExperimentalCoilApi::class)
class ThumbnailDiskCache(
    private val diskCache: DiskCache?,
    private val options: Options,
    private val fileType: String,
    private val file: File,
    /**
     * Whether the fetcher extracts at the size it was asked for, as video and PDF do. The others
     * store the image the file already carries — album art, an EPUB cover, an APK icon at the
     * screen's density — which covers any request whatever size it asked for.
     */
    variesWithSize: Boolean
) {
    /** What a cached entry has to cover to satisfy this request. */
    private val required: String = if (variesWithSize) {
        coverage(maxOf(options.thumbnailWidth(), options.thumbnailHeight()))
    } else {
        ANY_SIZE
    }

    /**
     * Reading the timestamp costs a stat, so the key is built only once an entry is actually looked
     * up. A [Fetcher][coil.fetch.Fetcher] is used by a single coroutine, hence the unsynchronized lazy.
     */
    private val key: String by lazy(LazyThreadSafetyMode.NONE) {
        thumbnailDiskCacheKey(fileType, file.absolutePath, file.lastModified())
    }

    /**
     * Returns the cached thumbnail, or null when there is none big enough for this request.
     * [mimeType] describes the bytes the matching [write] stored; pass null to let the decoder
     * detect it.
     */
    fun read(mimeType: String?): SourceResult? {
        val cache = diskCache ?: return null
        if (!options.diskCachePolicy.readEnabled) {
            return null
        }

        val snapshot = try {
            cache.openSnapshot(key)
        } catch (e: Exception) {
            ErrorReporter.warning(e, "read_thumbnail_disk_cache", fileType)
            null
        } ?: return null

        // An entry cached for a smaller request is not enough: it would be upscaled into a larger
        // slot. Treating it as a miss re-extracts at the size now needed and overwrites it, so the
        // entry settles at the largest size the app has asked for and serves every smaller one.
        if (!covers(cache.storedCoverage(snapshot), required)) {
            snapshot.close()
            return null
        }

        // The snapshot keeps the entry from being evicted while it is being read, and is closed by
        // Coil along with the source it is attached to.
        return SourceResult(
            source = ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
            mimeType = mimeType,
            dataSource = DataSource.DISK
        )
    }

    /**
     * Stores [bytes] for the next request. Pass a copy rather than the buffer handed to Coil: this
     * consumes what it is given.
     */
    fun write(bytes: Buffer) {
        val cache = diskCache ?: return
        if (!options.diskCachePolicy.writeEnabled) {
            return
        }
        // Video, PDF and APK thumbnails are tens of kilobytes, but audio album art and EPUB covers
        // are stored as the artwork the file embeds, at whatever resolution its author chose. A
        // single multi-megabyte cover would evict hundreds of ordinary thumbnails, so it is left
        // out rather than allowed to empty the cache.
        if (bytes.size > MAX_ENTRY_BYTES) {
            return
        }

        // Null when another fetch of the same file is already writing this entry; that one's result
        // is as good as this one's, so there is nothing to do.
        val editor = try {
            cache.openEditor(key)
        } catch (e: Exception) {
            ErrorReporter.warning(e, "write_thumbnail_disk_cache", fileType)
            null
        } ?: return

        try {
            cache.fileSystem.write(editor.data) {
                writeAll(bytes)
            }
            cache.fileSystem.write(editor.metadata) {
                writeUtf8(required)
            }
            editor.commit()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
            ErrorReporter.warning(e, "write_thumbnail_disk_cache", fileType)
        }
    }

    /** The size an entry covers, or null when it cannot be read — which counts as covering nothing. */
    private fun DiskCache.storedCoverage(snapshot: DiskCache.Snapshot): String? {
        return try {
            fileSystem.read(snapshot.metadata) { readUtf8() }
        } catch (e: Exception) {
            ErrorReporter.warning(e, "read_thumbnail_disk_cache", fileType)
            null
        }
    }

    companion object {
        const val MAX_ENTRY_BYTES = 512L * 1024L
    }
}

/**
 * Builds the key a file's extracted thumbnail is stored under. The timestamp invalidates the entry
 * when the file is edited in place, and the [fileType] prefix keeps the fetchers' entries apart
 * from each other and from anything Coil stores in the same cache.
 *
 * The format is otherwise free-form: the cache hashes the key before it reaches the file system, so
 * nothing here has to be a valid file name.
 */
fun thumbnailDiskCacheKey(fileType: String, path: String, lastModified: Long): String =
    "$fileType:$path:$lastModified"

/**
 * The key [file]'s extracted thumbnail would be cached under, or null when it is not one of the
 * types that has one — which is almost every file, and is settled from the extension alone. Call it
 * before deleting the file: the key includes the modification time, and only this reads it, so a
 * delete walking a large tree pays no stat for the files that have nothing cached.
 */
fun thumbnailDiskCacheKeyFor(file: File): String? {
    val fileType = ThumbnailFileType.of(MimeTypeUtil.getMimeType(file)) ?: return null

    return thumbnailDiskCacheKey(fileType, file.absolutePath, file.lastModified())
}

/**
 * Drops the entry at [key], obtained from [thumbnailDiskCacheKeyFor] before the file was deleted,
 * so a deleted file's thumbnail does not sit in the cache until eviction reclaims it. Silent when
 * nothing was cached under it.
 */
@OptIn(ExperimentalCoilApi::class)
fun evictThumbnail(diskCache: DiskCache?, key: String) {
    val cache = diskCache ?: return

    try {
        cache.remove(key)
    } catch (e: Exception) {
        ErrorReporter.warning(e, "evict_thumbnail_disk_cache")
    }
}

/**
 * The fetchers that extract a thumbnail rather than decoding the file as an image. Named here
 * because both the fetchers and [evictThumbnail] have to agree on the name, and eviction has to
 * work out from a path alone which fetcher would have cached it.
 */
object ThumbnailFileType {
    const val PDF = "pdf"
    const val VIDEO = "video"
    const val APK = "apk"
    const val AUDIO = "audio"
    const val EPUB = "epub"

    /**
     * The fetcher that caches thumbnails for [mimeType], or null when none does. Tested in the
     * order the fetchers are registered in AppImageLoader, so a type matching more than one
     * resolves to the same fetcher Coil would have picked.
     */
    fun of(mimeType: String): String? = when {
        MimeTypeUtil.isPdf(mimeType) -> PDF
        MimeTypeUtil.isVideo(mimeType) -> VIDEO
        MimeTypeUtil.isApk(mimeType) -> APK
        MimeTypeUtil.isAudio(mimeType) -> AUDIO
        MimeTypeUtil.isEpub(mimeType) -> EPUB
        else -> null
    }
}

/**
 * The size a request needs covered, resolving the dimensions Coil leaves undefined the same way the
 * fetchers do when they extract.
 */
internal fun Options.thumbnailWidth(): Int = size.width.pxOrElse { DEFAULT_THUMBNAIL_SIZE }

internal fun Options.thumbnailHeight(): Int = size.height.pxOrElse { DEFAULT_THUMBNAIL_SIZE }

internal const val DEFAULT_THUMBNAIL_SIZE = 120

internal fun Dimension.pxOrElse(default: () -> Int): Int =
    if (this is Dimension.Pixels) px else default()

/** Marks an entry that covers any request, because its bytes are the same whatever size was asked. */
private const val ANY_SIZE = "*"

/**
 * How much an entry covers, recorded as the longest side of the box it was extracted for. Reducing
 * the box to one number is what makes coverage totally ordered: two requests can always be ranked,
 * so a file's entry settles at the largest ever asked for. Comparing width and height separately
 * would leave requests of opposing aspect ratios each failing to cover the other, and a file shown
 * at both would re-extract forever.
 *
 * This bounds what was produced only for a square request box, which is what every thumbnail site
 * in the app asks for today. Video fits its frame inside the box, so its longest side is at most
 * the recorded one either way; PDF scales by width alone (see PdfThumbnailFetcher), so a taller
 * box would be recorded as covering more than the render actually spans, and a later request that
 * the bytes cannot satisfy would be served upscaled rather than re-extracted. A non-square
 * thumbnail request has to make the PDF fetcher honour both axes first.
 */
private fun coverage(longestSide: Int): String = longestSide.toString()

/**
 * Whether an entry recording [stored] satisfies a request needing [required]. A larger thumbnail
 * covers a smaller request, since Coil downsamples while decoding; an unreadable or unrecognised
 * record covers nothing, which re-extracts and overwrites it.
 */
private fun covers(stored: String?, required: String): Boolean {
    if (stored == null) return false
    if (stored == ANY_SIZE) return true
    // A sized entry cannot be trusted to be the file's own artwork, which is what a fetcher asking
    // for ANY_SIZE stores; re-extracting replaces it with one that is.
    if (required == ANY_SIZE) return false

    val storedSide = stored.toIntOrNull() ?: return false
    val requiredSide = required.toIntOrNull() ?: return false
    return storedSide >= requiredSide
}
