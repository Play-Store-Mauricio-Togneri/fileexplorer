package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.size.Size
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests for the store that keeps extracted thumbnails across process restarts.
 *
 * Coil writes its disk cache from its HTTP fetcher alone, so without this store a video frame, PDF
 * page, APK icon, album art or EPUB cover lived in the memory cache and nowhere else: every restart,
 * and every `onTrimMemory` that cleared it, meant extracting it again — for video, a full
 * MediaMetadataRetriever decode per thumbnail, throttled to a few at a time.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
class ThumbnailDiskCacheTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDir: File
    private lateinit var diskCache: DiskCache
    private lateinit var file: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "thumbnail_disk_cache_test_${System.nanoTime()}").apply { mkdirs() }
        diskCache = DiskCache.Builder()
            .directory(File(testDir, "cache").toOkioPath())
            .maxSizeBytes(10L * 1024 * 1024)
            .build()
        file = File(testDir, "clip.mp4").apply { writeBytes(ByteArray(16)) }
    }

    @After
    fun tearDown() {
        diskCache.clear()
        testDir.deleteRecursively()
    }

    @Test
    fun read_missesWhenNothingIsCached() {
        assertNull(cache().read(MIME_TYPE))
    }

    @Test
    fun read_returnsWhatWasWritten() {
        cache().write(buffer(THUMBNAIL))

        val result = requireResult(cache().read(MIME_TYPE))
        assertArrayEquals(THUMBNAIL, result.bytes())
        assertEquals(MIME_TYPE, result.mimeType)
        assertEquals(DataSource.DISK, result.dataSource)
        result.source.close()
    }

    // The entry outlives the process, so a file edited in place would show the thumbnail of its
    // previous content indefinitely if the timestamp were not part of the key.
    @Test
    fun read_missesAfterTheFileIsModified() {
        cache().write(buffer(THUMBNAIL))
        assertTrue(file.setLastModified(file.lastModified() + 10_000))

        assertNull(cache().read(MIME_TYPE))
    }

    // ---- one entry per file, covering the largest size asked for ----

    // Serving a thumbnail extracted for a list row to the item info screen would upscale it, so the
    // smaller entry counts as a miss and gets re-extracted at the size now needed.
    @Test
    fun read_missesWhenTheEntryIsSmallerThanTheRequest() {
        cache(size = Size(120, 120)).write(buffer(THUMBNAIL))

        assertNull(cache(size = Size(400, 400)).read(MIME_TYPE))
    }

    // The other direction is the point of recording the size rather than keying by it: one entry
    // serves every smaller request, because Coil downsamples it while decoding.
    @Test
    fun read_hitsWhenTheEntryIsLargerThanTheRequest() {
        cache(size = Size(400, 400)).write(buffer(THUMBNAIL))

        val result = requireResult(cache(size = Size(120, 120)).read(MIME_TYPE))
        assertArrayEquals(THUMBNAIL, result.bytes())
        result.source.close()
    }

    // So a file ends up with one entry at the largest size the app has asked for, not one per size.
    @Test
    fun write_replacesTheEntryWhenALargerSizeIsExtracted() {
        cache(size = Size(120, 120)).write(buffer(THUMBNAIL))
        cache(size = Size(400, 400)).write(buffer(LARGER_THUMBNAIL))

        val result = requireResult(cache(size = Size(120, 120)).read(MIME_TYPE))
        assertArrayEquals(LARGER_THUMBNAIL, result.bytes())
        result.source.close()
    }

    // Album art, EPUB covers and APK icons are stored as the file carries them, so they satisfy any
    // request however large.
    @Test
    fun read_hitsAtAnySizeWhenExtractionDoesNotDependOnIt() {
        cache(size = Size(120, 120), variesWithSize = false).write(buffer(THUMBNAIL))

        val result = requireResult(cache(size = Size(400, 400), variesWithSize = false).read(MIME_TYPE))
        assertArrayEquals(THUMBNAIL, result.bytes())
        result.source.close()
    }

    // ---- eviction ----

    // What a deleted file's thumbnail goes through, so it does not linger until the cache fills up.
    @Test
    fun evictThumbnail_dropsTheEntry() {
        cache().write(buffer(THUMBNAIL))
        val key = requireNotNull(thumbnailDiskCacheKeyFor(file))

        evictThumbnail(diskCache, key)

        assertNull(cache().read(MIME_TYPE))
    }

    // Deleting one file must not disturb another's thumbnail.
    @Test
    fun evictThumbnail_leavesOtherFilesAlone() {
        cache().write(buffer(THUMBNAIL))
        val other = File(testDir, "other.mp4").apply { writeBytes(ByteArray(8)) }

        evictThumbnail(diskCache, requireNotNull(thumbnailDiskCacheKeyFor(other)))

        val result = requireResult(cache().read(MIME_TYPE))
        result.source.close()
    }

    // Most files have no extracted thumbnail at all. A delete walks every file in a tree, so those
    // have to settle out from the name alone rather than each costing a stat.
    @Test
    fun thumbnailDiskCacheKey_isAbsentForFilesWithoutAnExtractedThumbnail() {
        val text = File(testDir, "notes.txt").apply { writeBytes(ByteArray(4)) }

        assertNull(thumbnailDiskCacheKeyFor(text))
        assertNotNull(thumbnailDiskCacheKeyFor(file))
    }

    @Test
    fun evictThumbnail_isNotAnErrorWithoutADiskCache() {
        evictThumbnail(null, requireNotNull(thumbnailDiskCacheKeyFor(file)))
    }

    // ---- size cap and policies ----

    // The EPUB and audio fetchers store the artwork their file embeds, so their entries cover a
    // request at any size. An empty one committed there would be a permanent hit on zero bytes.
    @Test
    fun write_skipsEmptyEntries() {
        cache(variesWithSize = false).write(buffer(ByteArray(0)))

        assertNull(cache(size = Size(400, 400), variesWithSize = false).read(MIME_TYPE))
    }

    // Audio album art and EPUB covers are stored at whatever resolution the file embeds. One
    // multi-megabyte cover would evict hundreds of ordinary thumbnails, so it is left out instead.
    @Test
    fun write_skipsEntriesOverTheSizeCap() {
        cache().write(buffer(ByteArray((ThumbnailDiskCache.MAX_ENTRY_BYTES + 1).toInt())))

        assertNull(cache().read(MIME_TYPE))
    }

    @Test
    fun write_storesEntriesUpToTheSizeCap() {
        val atCap = ByteArray(ThumbnailDiskCache.MAX_ENTRY_BYTES.toInt())
        cache().write(buffer(atCap))

        val result = requireResult(cache().read(MIME_TYPE))
        assertArrayEquals(atCap, result.bytes())
        result.source.close()
    }

    @Test
    fun cachePolicy_disablesWriting() {
        cache(policy = CachePolicy.READ_ONLY).write(buffer(THUMBNAIL))

        assertNull(cache().read(MIME_TYPE))
    }

    @Test
    fun cachePolicy_disablesReading() {
        cache().write(buffer(THUMBNAIL))

        assertNull(cache(policy = CachePolicy.WRITE_ONLY).read(MIME_TYPE))
    }

    // A loader can be built without a disk cache; the fetchers must still extract normally.
    @Test
    fun missingDiskCache_isNotAnError() {
        val cache = ThumbnailDiskCache(null, options(), FILE_TYPE, file, variesWithSize = true)

        cache.write(buffer(THUMBNAIL))
        assertNull(cache.read(MIME_TYPE))
    }

    // ---- helpers ----

    private fun cache(
        size: Size = Size(120, 120),
        policy: CachePolicy = CachePolicy.ENABLED,
        variesWithSize: Boolean = true
    ) = ThumbnailDiskCache(diskCache, options(size, policy), FILE_TYPE, file, variesWithSize)

    private fun options(
        size: Size = Size(120, 120),
        policy: CachePolicy = CachePolicy.ENABLED
    ) = Options(context = context, size = size, diskCachePolicy = policy)

    private fun buffer(bytes: ByteArray) = Buffer().apply { write(bytes) }

    private fun SourceFetchResult.bytes(): ByteArray = source.source().readByteArray()

    private fun requireResult(result: SourceFetchResult?): SourceFetchResult {
        assertNotNull("expected a cached thumbnail", result)
        return result!!
    }

    private companion object {
        const val FILE_TYPE = ThumbnailFileType.VIDEO
        const val MIME_TYPE = "image/jpeg"
        val THUMBNAIL = byteArrayOf(1, 2, 3, 4, 5)
        val LARGER_THUMBNAIL = byteArrayOf(6, 7, 8, 9, 10, 11)
    }
}
