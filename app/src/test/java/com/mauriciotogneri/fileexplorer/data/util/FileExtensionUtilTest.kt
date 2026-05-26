package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileExtensionUtilTest {

    @Test
    fun `getExtension returns lowercase extension for normal file`() {
        assertEquals("pdf", FileExtensionUtil.getExtension("/path/to/document.pdf"))
    }

    @Test
    fun `getExtension returns lowercase for uppercase extension`() {
        assertEquals("pdf", FileExtensionUtil.getExtension("/path/to/DOCUMENT.PDF"))
    }

    @Test
    fun `getExtension returns unknown for file without extension`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/README"))
    }

    @Test
    fun `getExtension handles dotfiles correctly`() {
        // .gitignore is treated as having extension "gitignore" by Java's File.extension
        assertEquals("gitignore", FileExtensionUtil.getExtension("/path/to/.gitignore"))
    }

    @Test
    fun `getExtension handles multiple dots correctly`() {
        assertEquals("gz", FileExtensionUtil.getExtension("/path/to/archive.tar.gz"))
    }

    @Test
    fun `getExtension handles long extensions`() {
        assertEquals("xhtml", FileExtensionUtil.getExtension("/path/to/file.xhtml"))
    }

    @Test
    fun `getExtension handles json extension`() {
        assertEquals("json", FileExtensionUtil.getExtension("/path/to/config.json"))
    }

    @Test
    fun `getExtension handles mixed case extension`() {
        assertEquals("jpg", FileExtensionUtil.getExtension("/path/to/image.JpG"))
    }
}
