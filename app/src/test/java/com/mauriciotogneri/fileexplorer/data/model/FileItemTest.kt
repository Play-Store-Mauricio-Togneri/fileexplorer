package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemTest {

    @Test
    fun `formattedSize returns formatted size`() {
        val file = createFileItem(size = 1024 * 1024)
        assertEquals("1 MB", file.formattedSize)
    }

    @Test
    fun `isImage returns true for image mime type`() {
        val file = createFileItem(mimeType = "image/png")
        assertTrue(file.isImage)
    }

    @Test
    fun `isImage returns false for non-image mime type`() {
        val file = createFileItem(mimeType = "application/pdf")
        assertFalse(file.isImage)
    }

    @Test
    fun `isPdf returns true for pdf mime type`() {
        val file = createFileItem(mimeType = "application/pdf")
        assertTrue(file.isPdf)
    }

    @Test
    fun `isAudio returns true for audio mime type`() {
        val file = createFileItem(mimeType = "audio/mpeg")
        assertTrue(file.isAudio)
    }

    @Test
    fun `isVideo returns true for video mime type`() {
        val file = createFileItem(mimeType = "video/mp4")
        assertTrue(file.isVideo)
    }

    @Test
    fun `thumbnailCacheKey combines path and last modified time`() {
        val file = createFileItem(path = "/storage/emulated/0/photo.jpg", lastModified = 1500L)
        assertEquals("/storage/emulated/0/photo.jpg:1500", file.thumbnailCacheKey)
    }

    @Test
    fun `thumbnailCacheKey changes when the file is modified`() {
        val before = createFileItem(lastModified = 1500L)
        val after = before.copy(lastModified = 1600L)
        assertNotEquals(before.thumbnailCacheKey, after.thumbnailCacheKey)
    }

    private fun createFileItem(
        path: String = "/storage/emulated/0/test.txt",
        name: String = "test.txt",
        isDirectory: Boolean = false,
        size: Long = 1024,
        lastModified: Long = System.currentTimeMillis(),
        createdTime: Long = System.currentTimeMillis(),
        mimeType: String = "text/plain",
        childCount: Int? = null
    ) = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        createdTime = createdTime,
        mimeType = mimeType,
        childCount = childCount
    )
}
