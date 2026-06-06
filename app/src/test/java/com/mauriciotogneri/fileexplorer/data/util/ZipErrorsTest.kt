package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.zip.ZipException

class ZipErrorsTest {

    @Test
    fun `isUnreadableZip returns true for missing END header ZipException`() {
        val e = ZipException("zip END header not found")
        assertTrue(isUnreadableZip(e))
    }

    @Test
    fun `isUnreadableZip returns true for any ZipException regardless of message`() {
        assertTrue(isUnreadableZip(ZipException()))
        assertTrue(isUnreadableZip(ZipException("invalid CEN header (bad signature)")))
    }

    @Test
    fun `isUnreadableZip returns false for a plain IOException`() {
        // A genuine I/O failure (not malformed ZIP content) must stay reportable.
        assertFalse(isUnreadableZip(IOException("zip END header not found")))
    }

    @Test
    fun `isUnreadableZip returns false for unrelated exceptions`() {
        assertFalse(isUnreadableZip(IllegalStateException("boom")))
        assertFalse(isUnreadableZip(RuntimeException()))
        assertFalse(isUnreadableZip(OutOfMemoryError()))
    }
}
