package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression tests for how thumbnail requests are keyed in the memory cache.
 *
 * Coil runs its interceptor chain on Dispatchers.Main.immediate, so building the default memory
 * cache key for a File calls File.lastModified() — a stat syscall on the main thread for every list
 * row, inside the measure pass, which ANR'd when storage was congested. The loaders therefore
 * disable that key component, and call sites holding a timestamp already read off the main thread
 * pass FileItem.thumbnailCacheKey instead. These tests pin both halves: the loader's own key must
 * not depend on the file's timestamp, and an explicit key must still invalidate when it changes.
 *
 * Requests use [Size.ORIGINAL] so a cached value stays valid regardless of downsampling arithmetic;
 * only the keys decide hit versus miss here.
 */
@RunWith(AndroidJUnit4::class)
class AppImageLoaderCacheKeyTest {

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(appContext.cacheDir, "cache_key_test_${System.nanoTime()}").apply { mkdirs() }
        // The loader's caches are process-wide singletons; start each test clean.
        AppImageLoader.thumbnails(appContext).memoryCache?.clear()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // ---- the loader's own key never reads the file's timestamp ----

    @Test
    fun loaderKey_isPathOnly() {
        val file = copyAsset()
        assertEquals(file.absolutePath, decode(file).memoryCacheKey?.key)
    }

    @Test
    fun loaderKey_ignoresChangedTimestamp() {
        val file = copyAsset()
        decode(file)
        assertTrue(file.setLastModified(file.lastModified() + 10_000))

        // The trade-off of dropping the stat: for call sites that pass no key of their own, an
        // in-place edit alone no longer invalidates the cached thumbnail.
        assertEquals(DataSource.MEMORY_CACHE, decode(file).dataSource)
    }

    // ---- an explicit key is honoured, and still invalidates on edit ----

    @Test
    fun explicitKey_isUsedVerbatim() {
        val file = copyAsset()
        assertEquals(key(file, 1500L), decode(file, key(file, 1500L)).memoryCacheKey?.key)
    }

    @Test
    fun explicitKey_hitsMemoryCacheWhenUnchanged() {
        val file = copyAsset()
        decode(file, key(file, 1500L))
        assertEquals(DataSource.MEMORY_CACHE, decode(file, key(file, 1500L)).dataSource)
    }

    @Test
    fun explicitKey_missesMemoryCacheWhenTimestampChanges() {
        val file = copyAsset()
        decode(file, key(file, 1500L))

        // What FileItem.thumbnailCacheKey buys back: an edited file re-renders its thumbnail.
        assertNotEquals(DataSource.MEMORY_CACHE, decode(file, key(file, 1600L)).dataSource)
    }

    // ---- helpers ----

    /** Mirrors the shape of FileItem.thumbnailCacheKey. */
    private fun key(file: File, lastModified: Long): String = "${file.absolutePath}:$lastModified"

    private fun copyAsset(): File {
        val file = File(testDir, "image.gif")
        testContext.assets.open("small_anim.gif").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun decode(file: File, memoryCacheKey: String? = null): SuccessResult {
        val result = runBlocking {
            AppImageLoader.thumbnails(appContext).execute(
                ImageRequest.Builder(appContext)
                    .data(file)
                    .memoryCacheKey(memoryCacheKey)
                    .size(Size.ORIGINAL)
                    .build()
            )
        }
        return when (result) {
            is SuccessResult -> result
            is ErrorResult -> throw AssertionError("decode failed for ${file.name}", result.throwable)
        }
    }
}
