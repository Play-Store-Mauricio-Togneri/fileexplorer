package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class VideoErrorsTest {

    @Test
    fun `isUnreadableVideo returns true for setDataSource failure`() {
        val e = RuntimeException("setDataSource failed: status = 0xFFFFFFEA")
        assertTrue(isUnreadableVideo(e))
    }

    @Test
    fun `isUnreadableVideo returns true when message contains the marker as a substring`() {
        val e = RuntimeException("MediaMetadataRetriever: setDataSource failed")
        assertTrue(isUnreadableVideo(e))
    }

    @Test
    fun `isUnreadableVideo returns true for IllegalArgumentException from setDataSource`() {
        // setDataSource(String) wraps a missing/unopenable path as IllegalArgumentException.
        assertTrue(isUnreadableVideo(IllegalArgumentException("/storage/emulated/0/clip.mp4 does not exist")))
        assertTrue(isUnreadableVideo(IllegalArgumentException("couldn't open /storage/emulated/0/clip.mp4")))
    }

    @Test
    fun `isUnreadableVideo returns true for IllegalArgumentException with no message`() {
        // setDataSource(null) throws an IllegalArgumentException with no message.
        assertTrue(isUnreadableVideo(IllegalArgumentException()))
    }

    @Test
    fun `isUnreadableVideo returns true for empty-message IllegalStateException from setDataSource`() {
        // The native layer throws an empty-message IllegalStateException when no
        // retriever is available (e.g. the media extractor service is unreachable).
        assertTrue(isUnreadableVideo(IllegalStateException()))
    }

    @Test
    fun `isUnreadableVideo returns true for any IllegalStateException regardless of message`() {
        assertTrue(isUnreadableVideo(IllegalStateException("some other wording")))
    }

    @Test
    fun `isUnreadableVideo returns false for RuntimeException with a different message`() {
        val e = RuntimeException("some other failure")
        assertFalse(isUnreadableVideo(e))
    }

    @Test
    fun `isUnreadableVideo returns false for RuntimeException with no message`() {
        val e = RuntimeException()
        assertFalse(isUnreadableVideo(e))
    }

    @Test
    fun `isUnreadableVideo returns false for non-RuntimeException exceptions`() {
        assertFalse(isUnreadableVideo(IOException("setDataSource failed")))
        assertFalse(isUnreadableVideo(OutOfMemoryError("setDataSource failed")))
    }
}
