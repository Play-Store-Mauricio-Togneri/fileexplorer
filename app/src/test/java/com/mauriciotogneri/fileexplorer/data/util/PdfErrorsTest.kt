package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PdfErrorsTest {

    @Test
    fun `isUnreadablePdf returns true for password-protected PDF`() {
        val e = SecurityException("password required or incorrect password")
        assertTrue(isUnreadablePdf(e))
    }

    @Test
    fun `isUnreadablePdf returns true for any SecurityException regardless of message`() {
        assertTrue(isUnreadablePdf(SecurityException()))
        assertTrue(isUnreadablePdf(SecurityException("some other wording")))
    }

    @Test
    fun `isUnreadablePdf returns true for corrupted PDF IOException`() {
        val e = IOException("Unable to load the document!")
        assertTrue(isUnreadablePdf(e))
    }

    @Test
    fun `isUnreadablePdf returns true for non-PDF IOException`() {
        val e = IOException("file not in PDF format or corrupted")
        assertTrue(isUnreadablePdf(e))
    }

    @Test
    fun `isUnreadablePdf returns true for any IOException regardless of message`() {
        assertTrue(isUnreadablePdf(IOException()))
        assertTrue(isUnreadablePdf(IOException("some other wording")))
    }

    @Test
    fun `isUnreadablePdf returns true for unloadable page IllegalStateException`() {
        val e = IllegalStateException("cannot load page")
        assertTrue(isUnreadablePdf(e))
    }

    @Test
    fun `isUnreadablePdf returns true for any IllegalStateException regardless of message`() {
        assertTrue(isUnreadablePdf(IllegalStateException()))
        assertTrue(isUnreadablePdf(IllegalStateException("some other wording")))
    }

    @Test
    fun `isUnreadablePdf returns false for unrelated exceptions`() {
        assertFalse(isUnreadablePdf(RuntimeException()))
        assertFalse(isUnreadablePdf(OutOfMemoryError()))
    }
}
