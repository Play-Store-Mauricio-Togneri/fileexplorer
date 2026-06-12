package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class ImageErrorsTest {

    @Test
    fun `isUnreadableImage returns true for a missing file FileNotFoundException`() {
        // The file vanished between the existence check and ExifInterface opening it.
        val e = FileNotFoundException("/path/photo.jpg (No such file or directory)")
        assertTrue(isUnreadableImage(e))
    }

    @Test
    fun `isUnreadableImage returns true for any IOException regardless of message`() {
        assertTrue(isUnreadableImage(IOException()))
        assertTrue(isUnreadableImage(IOException("some other wording")))
    }

    @Test
    fun `isUnreadableImage returns false for unrelated exceptions`() {
        assertFalse(isUnreadableImage(IllegalStateException("boom")))
        assertFalse(isUnreadableImage(IllegalArgumentException()))
        assertFalse(isUnreadableImage(RuntimeException()))
        assertFalse(isUnreadableImage(OutOfMemoryError()))
    }
}
