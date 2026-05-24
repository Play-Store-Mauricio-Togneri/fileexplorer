package com.mauriciotogneri.fileexplorer.data.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FileSizeFormatterTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `format returns 0 B for zero bytes`() {
        assertEquals("0 B", FileSizeFormatter.format(0))
    }

    @Test
    fun `format returns 0 B for negative bytes`() {
        assertEquals("0 B", FileSizeFormatter.format(-100))
    }

    @Test
    fun `format returns bytes for small values`() {
        assertEquals("1 B", FileSizeFormatter.format(1))
        assertEquals("512 B", FileSizeFormatter.format(512))
        assertEquals("1,023 B", FileSizeFormatter.format(1023))
    }

    @Test
    fun `format returns KB for kilobyte range`() {
        assertEquals("1 KB", FileSizeFormatter.format(1024))
        assertEquals("1.5 KB", FileSizeFormatter.format(1536))
        assertEquals("10 KB", FileSizeFormatter.format(10 * 1024))
    }

    @Test
    fun `format returns MB for megabyte range`() {
        assertEquals("1 MB", FileSizeFormatter.format(1024 * 1024))
        assertEquals("5.5 MB", FileSizeFormatter.format((5.5 * 1024 * 1024).toLong()))
        assertEquals("100 MB", FileSizeFormatter.format(100 * 1024 * 1024))
    }

    @Test
    fun `format returns GB for gigabyte range`() {
        assertEquals("1 GB", FileSizeFormatter.format(1024L * 1024 * 1024))
        assertEquals("2.5 GB", FileSizeFormatter.format((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `format returns TB for terabyte range`() {
        assertEquals("1 TB", FileSizeFormatter.format(1024L * 1024 * 1024 * 1024))
        assertEquals("4 TB", FileSizeFormatter.format(4L * 1024 * 1024 * 1024 * 1024))
    }
}
