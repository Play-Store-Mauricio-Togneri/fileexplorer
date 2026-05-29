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
    fun `isUnreadablePdf returns false for IOException with different message`() {
        val e = IOException("disk read failed")
        assertFalse(isUnreadablePdf(e))
    }

    @Test
    fun `isUnreadablePdf returns false for unrelated exceptions`() {
        assertFalse(isUnreadablePdf(IllegalStateException("boom")))
        assertFalse(isUnreadablePdf(RuntimeException()))
        assertFalse(isUnreadablePdf(OutOfMemoryError()))
    }
}
