package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.drawable.ScaleDrawable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression tests for GIF animation in the internal image viewer.
 *
 * The `thumbnails` loader has no animated decoder, so it decodes a GIF's static first frame; the
 * `viewer` loader must always animate. The two loaders once shared one memory cache, and because
 * Coil keys transformation-free requests by file path only (the requested size is absent from the
 * key), the viewer was served the thumbnail's frozen frame for any GIF whose pixel size was at or
 * below its thumbnail size. Giving each loader its own memory cache fixed it: every case below must
 * decode an animated drawable, whether or not the file was thumbnailed first and at any size.
 *
 * An animated result is a [ScaleDrawable] wrapping [AnimatedImageDrawable]; a static first frame is
 * a plain [BitmapDrawable] (what the memory cache stores). Requests use [Precision.INEXACT] to match
 * what AsyncImagePainter applies to every Compose image request.
 *
 * Assets (app/src/androidTest/assets): small_anim.gif = 64x64 x3 frames, large_anim.gif = 512x512 x3.
 */
@RunWith(AndroidJUnit4::class)
class AppImageLoaderGifTest {

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(appContext.cacheDir, "gif_test_${System.nanoTime()}").apply { mkdirs() }
        // The loaders' caches are process-wide singletons; start each test clean.
        AppImageLoader.thumbnails(appContext).memoryCache?.clear()
        AppImageLoader.viewer(appContext).memoryCache?.clear()
        AppImageLoader.viewer(appContext).diskCache?.clear()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // ---- the viewer decodes GIFs as animated, on its own ----

    @Test
    fun viewer_largeGif_isAnimated() {
        val file = copyAsset("large_anim.gif", "large.gif")
        assertEquals("animated", classify(decode(AppImageLoader.viewer(appContext), file, 4096)))
    }

    @Test
    fun viewer_smallGif_isAnimated() {
        val file = copyAsset("small_anim.gif", "small.gif")
        assertEquals("animated", classify(decode(AppImageLoader.viewer(appContext), file, 4096)))
    }

    // ---- and still animates after the file was thumbnailed first (the fixed bug) ----

    @Test
    fun viewer_afterListThumbnail_largeGif_isAnimated() {
        val file = copyAsset("large_anim.gif", "large.gif")
        decode(AppImageLoader.thumbnails(appContext), file, 120)
        assertEquals("animated", classify(decode(AppImageLoader.viewer(appContext), file, 4096)))
    }

    @Test
    fun viewer_afterListThumbnail_smallGif_isAnimated() {
        val file = copyAsset("small_anim.gif", "small.gif")
        // Folder-list thumbnail (@120) caches a static first frame; a <=120px GIF is not sampled.
        decode(AppImageLoader.thumbnails(appContext), file, 120)
        assertEquals("animated", classify(decode(AppImageLoader.viewer(appContext), file, 4096)))
    }

    @Test
    fun viewer_afterItemInfoThumbnail_smallGif_isAnimated() {
        val file = copyAsset("small_anim.gif", "small.gif")
        // Item Info thumbnail (@400) caches a static first frame; a <=400px GIF is not sampled.
        decode(AppImageLoader.thumbnails(appContext), file, 400)
        assertEquals("animated", classify(decode(AppImageLoader.viewer(appContext), file, 4096)))
    }

    // ---- helpers ----

    private fun copyAsset(assetName: String, outName: String): File {
        val file = File(testDir, outName)
        testContext.assets.open(assetName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun decode(loader: ImageLoader, file: File, size: Int): Drawable {
        val result = runBlocking {
            loader.execute(
                ImageRequest.Builder(appContext)
                    .data(file)
                    .size(size)
                    // AsyncImagePainter forces INEXACT for Compose requests (AsyncImagePainter.kt);
                    // execute() alone leaves precision AUTOMATIC, which changes cache-hit behavior.
                    .precision(Precision.INEXACT)
                    .build()
            )
        }
        return when (result) {
            is SuccessResult -> result.drawable
            is ErrorResult -> throw AssertionError("decode failed for ${file.name} @ $size", result.throwable)
        }
    }

    private fun classify(drawable: Drawable): String = when {
        drawable is ScaleDrawable && drawable.child is AnimatedImageDrawable -> "animated"
        drawable is AnimatedImageDrawable -> "animated"
        drawable is BitmapDrawable -> "static"
        else -> "other:${drawable::class.java.simpleName}"
    }
}
