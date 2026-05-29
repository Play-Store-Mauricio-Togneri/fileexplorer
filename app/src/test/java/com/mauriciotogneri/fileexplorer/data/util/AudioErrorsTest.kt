package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AudioErrorsTest {

    @Test
    fun `isUnreadableAudio returns true for setDataSource failure`() {
        val e = RuntimeException("setDataSource failed: status = 0x80000000")
        assertTrue(isUnreadableAudio(e))
    }

    @Test
    fun `isUnreadableAudio returns true when message contains the marker as a substring`() {
        val e = IllegalStateException("MediaMetadataRetriever: setDataSource failed")
        assertTrue(isUnreadableAudio(e))
    }

    @Test
    fun `isUnreadableAudio returns false for RuntimeException with a different message`() {
        val e = RuntimeException("some other failure")
        assertFalse(isUnreadableAudio(e))
    }

    @Test
    fun `isUnreadableAudio returns false for RuntimeException with no message`() {
        val e = RuntimeException()
        assertFalse(isUnreadableAudio(e))
    }

    @Test
    fun `isUnreadableAudio returns false for non-RuntimeException exceptions`() {
        assertFalse(isUnreadableAudio(IOException("setDataSource failed")))
        assertFalse(isUnreadableAudio(OutOfMemoryError("setDataSource failed")))
    }
}
