package com.mauriciotogneri.fileexplorer.data.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TextFilePreviewTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("text_file_preview").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `reads a small file into lines`() {
        val file = File(dir, "a.txt").apply { writeText("line1\nline2\nline3") }

        val result = TextFilePreview.read(file, maxBytes = 1024)

        assertEquals(listOf("line1", "line2", "line3"), result.lines)
        assertFalse(result.truncated)
    }

    @Test
    fun `strips carriage returns from CRLF line endings`() {
        val file = File(dir, "b.txt").apply { writeText("a\r\nb\r\n") }

        val result = TextFilePreview.read(file, maxBytes = 1024)

        assertEquals(listOf("a", "b", ""), result.lines)
    }

    @Test
    fun `empty file yields no lines and is not truncated`() {
        val file = File(dir, "c.txt").apply { writeText("") }

        val result = TextFilePreview.read(file, maxBytes = 1024)

        assertTrue(result.lines.isEmpty())
        assertFalse(result.truncated)
    }

    @Test
    fun `flags truncation when file exceeds the cap`() {
        val file = File(dir, "d.txt").apply { writeText("abcdefghij") }

        val result = TextFilePreview.read(file, maxBytes = 4)

        assertTrue(result.truncated)
        assertEquals(listOf("abcd"), result.lines)
    }

    @Test
    fun `does not flag truncation when file exactly fills the cap`() {
        val file = File(dir, "e.txt").apply { writeText("abcd") }

        val result = TextFilePreview.read(file, maxBytes = 4)

        assertFalse(result.truncated)
        assertEquals(listOf("abcd"), result.lines)
    }

    @Test
    fun `decodes utf-8 multibyte content`() {
        val file = File(dir, "f.txt").apply { writeText("café — déjà\nセール") }

        val result = TextFilePreview.read(file, maxBytes = 1024)

        assertEquals(listOf("café — déjà", "セール"), result.lines)
    }
}
