package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemTest {

    @Test
    fun `extension extracts uppercase extension for short extensions`() {
        val file = createFileItem(name = "document.pdf")
        assertEquals("PDF", file.extension)
    }

    @Test
    fun `extension extracts uppercase extension up to 4 chars`() {
        val file = createFileItem(name = "archive.json")
        assertEquals("JSON", file.extension)
    }

    @Test
    fun `extension returns empty for extensions longer than 4 chars`() {
        val file = createFileItem(name = "file.xhtml")
        assertEquals("", file.extension)
    }

    @Test
    fun `extension returns empty for files without extension`() {
        val file = createFileItem(name = "README")
        assertEquals("", file.extension)
    }

    @Test
    fun `extension handles dotfiles correctly`() {
        val file = createFileItem(name = ".gitignore")
        assertEquals("", file.extension)
    }

    @Test
    fun `extension handles multiple dots correctly`() {
        val file = createFileItem(name = "archive.tar.gz")
        assertEquals("GZ", file.extension)
    }

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

    private fun createFileItem(
        path: String = "/storage/emulated/0/test.txt",
        name: String = "test.txt",
        isDirectory: Boolean = false,
        size: Long = 1024,
        lastModified: Long = System.currentTimeMillis(),
        mimeType: String = "text/plain",
        childCount: Int? = null
    ) = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        mimeType = mimeType,
        childCount = childCount
    )
}
