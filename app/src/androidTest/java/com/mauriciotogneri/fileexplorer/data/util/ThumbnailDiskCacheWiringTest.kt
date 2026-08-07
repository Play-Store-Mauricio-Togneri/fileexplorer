package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.annotation.ExperimentalCoilApi
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Size
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks that a thumbnail fetcher is actually wired to [ThumbnailDiskCache] — that it writes what it
 * extracts and reads it back on the next request. [ThumbnailDiskCacheTest] covers the store itself;
 * what is left to get wrong is a fetcher that never calls it, which no compiler error would catch
 * and which would silently restore the old behaviour of re-extracting every thumbnail after every
 * restart.
 *
 * Driven through the APK fetcher because an APK is the one file type with an extractable thumbnail
 * that the test can produce on device: the app under test is installed, so its own archive is on
 * disk. All five fetchers use the store the same way.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
class ThumbnailDiskCacheWiringTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDir: File
    private lateinit var apk: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "thumbnail_wiring_test_${System.nanoTime()}").apply { mkdirs() }
        apk = File(testDir, "app.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk)
        // The loader's caches are process-wide singletons; start clean.
        AppImageLoader.thumbnails(context).memoryCache?.clear()
    }

    @After
    fun tearDown() {
        // Guarded because setUp copies the installed APK, and a failure there would otherwise
        // surface here as an unrelated UninitializedPropertyAccessException.
        if (::apk.isInitialized) {
            // The disk cache is the app's real one, so take the entry this test added back out.
            AppImageLoader.thumbnails(context).diskCache?.remove(cacheKey())
        }
        if (::testDir.isInitialized) {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun extractedThumbnail_isWrittenToTheDiskCache() {
        load()

        val snapshot = AppImageLoader.thumbnails(context).diskCache?.openSnapshot(cacheKey())
        assertNotNull("the extracted thumbnail was not persisted", snapshot)
        snapshot?.close()
    }

    @Test
    fun cachedThumbnail_isReadBackWithoutExtractingAgain() {
        load()

        // Replace the archive's contents while keeping its timestamp, so the cache key still matches
        // but nothing can be extracted from the file any more. A second load that still succeeds can
        // only have come from what was written to disk.
        val timestamp = apk.lastModified()
        apk.writeBytes(ByteArray(64))
        assertTrue(apk.setLastModified(timestamp))
        AppImageLoader.thumbnails(context).memoryCache?.clear()

        load()
    }

    /**
     * The control for the test above: with nothing cached, the same replaced contents produce no
     * thumbnail at all. Without this, that test would still pass if the archive were somehow
     * readable after being overwritten, and would be proving nothing.
     */
    @Test
    fun replacedArchive_hasNoThumbnailOfItsOwn() {
        apk.writeBytes(ByteArray(64))

        assertTrue(loadResult() is ErrorResult)
    }

    /**
     * Deleting a file through the repository drops its thumbnail, rather than leaving it in the
     * cache until eviction reclaims it.
     */
    @Test
    fun deletingTheFile_dropsItsThumbnail() {
        load()
        val key = cacheKey()

        runBlocking { FileRepository().delete(listOf(FileItem.from(apk))) }

        val snapshot = AppImageLoader.thumbnails(context).diskCache?.openSnapshot(key)
        snapshot?.close()
        assertNull("the deleted file's thumbnail is still cached", snapshot)
    }

    // ---- helpers ----

    private fun cacheKey(): String =
        thumbnailDiskCacheKey(ThumbnailFileType.APK, apk.absolutePath, apk.lastModified())

    /** Loads the APK's thumbnail, failing the test if it cannot be produced. */
    private fun load(): SuccessResult {
        return when (val result = loadResult()) {
            is SuccessResult -> result
            is ErrorResult -> throw AssertionError("no thumbnail for ${apk.name}", result.throwable)
        }
    }

    private fun loadResult(): ImageResult = runBlocking {
        AppImageLoader.thumbnails(context).execute(
            ImageRequest.Builder(context)
                .data(apk)
                .size(SIZE)
                .build()
        )
    }

    private companion object {
        val SIZE = Size(120, 120)
    }
}
