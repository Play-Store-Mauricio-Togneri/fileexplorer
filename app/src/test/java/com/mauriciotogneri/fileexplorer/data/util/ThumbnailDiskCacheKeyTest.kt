package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailDiskCacheKeyTest {

    @Test
    fun `key combines file type, path and last modified time`() {
        assertEquals(
            "video:$PATH:1500",
            thumbnailDiskCacheKey(ThumbnailFileType.VIDEO, PATH, 1500L)
        )
    }

    // Without this an edited file keeps showing the thumbnail of its previous content, and unlike
    // the memory cache the entry survives restarts, so it would never correct itself.
    @Test
    fun `key changes when the file is modified`() {
        assertNotEquals(key(lastModified = 1500L), key(lastModified = 1600L))
    }

    @Test
    fun `key changes with the file type`() {
        assertNotEquals(key(fileType = ThumbnailFileType.VIDEO), key(fileType = ThumbnailFileType.AUDIO))
    }

    // The size requested is deliberately absent: a file has one entry whatever sizes the list rows,
    // home cards and item info screen ask for, which is also what lets a deleted file's entry be
    // named without knowing which of those it was ever shown in.
    @Test
    fun `key is stable for an unchanged file`() {
        assertEquals(key(), key())
    }

    // ---- the fetcher a path resolves to ----

    @Test
    fun `file type follows the mime type`() {
        assertEquals(ThumbnailFileType.VIDEO, ThumbnailFileType.of("video/mp4"))
        assertEquals(ThumbnailFileType.AUDIO, ThumbnailFileType.of("audio/mpeg"))
        assertEquals(ThumbnailFileType.PDF, ThumbnailFileType.of("application/pdf"))
        assertEquals(ThumbnailFileType.APK, ThumbnailFileType.of("application/vnd.android.package-archive"))
        assertEquals(ThumbnailFileType.EPUB, ThumbnailFileType.of("application/epub+zip"))
    }

    // Plain images are decoded by Coil itself and never reach a thumbnail fetcher, so nothing of
    // theirs is in this cache to evict.
    @Test
    fun `file type is absent for types without an extracted thumbnail`() {
        assertNull(ThumbnailFileType.of("image/jpeg"))
        assertNull(ThumbnailFileType.of("text/plain"))
        assertNull(ThumbnailFileType.of("*/*"))
    }

    private fun key(
        fileType: String = ThumbnailFileType.VIDEO,
        lastModified: Long = 1500L
    ): String = thumbnailDiskCacheKey(fileType, PATH, lastModified)

    private companion object {
        const val PATH = "/storage/emulated/0/clip.mp4"
    }
}
