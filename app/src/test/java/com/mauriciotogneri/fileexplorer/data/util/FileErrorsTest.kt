package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class FileErrorsTest {

    @Test
    fun `isUnreadableFile returns true for a missing file FileNotFoundException`() {
        // The file vanished between the existence check and the reader opening it.
        val e = FileNotFoundException("/path/notes.txt: open failed: ENOENT (No such file or directory)")
        assertTrue(isUnreadableFile(e))
    }

    @Test
    fun `isUnreadableFile returns true for any IOException regardless of message`() {
        assertTrue(isUnreadableFile(IOException()))
        assertTrue(isUnreadableFile(IOException("some other wording")))
    }

    @Test
    fun `isUnreadableFile returns false for unrelated exceptions`() {
        // A defect in the app's own handling of bytes it did read must stay reportable.
        assertFalse(isUnreadableFile(StringIndexOutOfBoundsException("length=3; index=7")))
        assertFalse(isUnreadableFile(SecurityException("denied")))
        assertFalse(isUnreadableFile(IllegalStateException("boom")))
        assertFalse(isUnreadableFile(IllegalArgumentException()))
        assertFalse(isUnreadableFile(RuntimeException()))
        assertFalse(isUnreadableFile(OutOfMemoryError()))
    }
}
