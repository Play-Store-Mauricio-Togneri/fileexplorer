package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for when the image loaders resolve their disk cache's directory.
 *
 * The loaders are built on the main thread, by the first list row that shows a thumbnail, and
 * reading the cache directory takes a lock Android also holds for the whole of a background
 * getExternalFilesDirs() — which waits on the storage daemon when a volume's app directory is
 * missing. A storage listing stuck there froze that first frame until it ANRed.
 */
class AppImageLoaderTest {

    private val context: Context = mockk()
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        cacheDir = File(tmpDir, "app_image_loader_test_${System.nanoTime()}")
        every { context.applicationContext } returns context
        every { context.cacheDir } returns cacheDir
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun build_doesNotReadTheCacheDirectory() {
        AppImageLoader.build(context)

        verify(exactly = 0) { context.cacheDir }
    }

    // Two caches over one directory would corrupt each other's journal, so deferring the cache must
    // not hand each loader an instance of its own.
    @Test
    fun diskCache_isOneInstanceSharedByBothLoaders() {
        val loaders = AppImageLoader.build(context)

        val thumbnails = loaders.thumbnails.diskCache
        val viewer = loaders.viewer.diskCache

        assertNotNull(thumbnails)
        assertSame(thumbnails, viewer)
        assertEquals(File(cacheDir, "image_cache").toOkioPath(), thumbnails?.directory)
        verify(exactly = 1) { context.cacheDir }
    }
}
