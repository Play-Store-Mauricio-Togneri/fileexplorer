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
    // must resolve to one key, or each screen holds its own copy of the thumbnail. (The screens
    // request different sizes, so the entry can still be re-decoded once when a smaller cached
    // bitmap cannot satisfy a larger request — but it stays one entry rather than three.)
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

    private companion object {
        const val PATH = "/storage/emulated/0/photo.jpg"
    }
}
