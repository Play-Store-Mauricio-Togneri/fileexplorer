package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThumbnailCacheKeyTest {

    @Test
    fun `key combines path and last modified time`() {
        assertEquals("/storage/emulated/0/photo.jpg:1500", thumbnailCacheKey("/storage/emulated/0/photo.jpg", 1500L))
    }

    @Test
    fun `key changes when the file is modified`() {
        assertNotEquals(thumbnailCacheKey(PATH, 1500L), thumbnailCacheKey(PATH, 1600L))
    }

    // The same file can appear in the folder list, in favorites and in recents at once. All three
    // must build the same base key, so that two of them drawing it at the same size share one
    // entry rather than holding a copy each.
    @Test
    fun `every model keys the same file identically`() {
        val fileItem = FileItem(
            path = PATH,
            name = "photo.jpg",
            isDirectory = false,
            size = 1024,
            lastModified = 1500L,
            createdTime = 900L,
            mimeType = "image/jpeg"
        )
        val favorite = Favorite(
            path = PATH,
            name = "photo.jpg",
            isDirectory = false,
            mimeType = "image/jpeg",
            favoritedTimestamp = 2000L,
            lastModified = 1500L
        )
        val recentFile = RecentFile(
            path = PATH,
            name = "photo.jpg",
            mimeType = "image/jpeg",
            lastOpenedTimestamp = 3000L,
            lastModified = 1500L
        )

        assertEquals(fileItem.thumbnailCacheKey, favorite.thumbnailCacheKey)
        assertEquals(fileItem.thumbnailCacheKey, recentFile.thumbnailCacheKey)
    }

    @Test
    fun `size qualifies the key`() {
        assertEquals("$PATH:1500@120", thumbnailCacheKeyAtSize(thumbnailCacheKey(PATH, 1500L), 120))
    }

    // Coil hands back a cached bitmap smaller than the request rather than re-decoding it, so the
    // 40.dp row and the 200.dp preview sharing a key would show the row's thumbnail in both.
    @Test
    fun `different sizes key differently`() {
        val base = thumbnailCacheKey(PATH, 1500L)

        assertNotEquals(thumbnailCacheKeyAtSize(base, 120), thumbnailCacheKeyAtSize(base, 600))
    }

    // The separator is what keeps the two key spaces apart. Appending the size without one would
    // make a thumbnail of PATH at 120 px collide with the unqualified entry PATH would have if it
    // were modified at 1500120 — the same file, a different timestamp, a stale thumbnail.
    @Test
    fun `a qualified key never collides with a base key`() {
        assertNotEquals(
            thumbnailCacheKeyAtSize(thumbnailCacheKey(PATH, 1500L), 120),
            thumbnailCacheKey(PATH, 1500120L)
        )
    }

    private companion object {
        const val PATH = "/storage/emulated/0/photo.jpg"
    }
}
