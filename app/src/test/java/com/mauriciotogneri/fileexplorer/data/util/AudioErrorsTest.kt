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
        val e = RuntimeException("MediaMetadataRetriever: setDataSource failed")
        assertTrue(isUnreadableAudio(e))
    }

    @Test
    fun `isUnreadableAudio returns true for IllegalArgumentException from setDataSource`() {
        // setDataSource(String) wraps a missing/unopenable path as IllegalArgumentException.
        assertTrue(isUnreadableAudio(IllegalArgumentException("/storage/emulated/0/song.mp3 does not exist")))
        assertTrue(isUnreadableAudio(IllegalArgumentException("couldn't open /storage/emulated/0/song.mp3")))
    }

    @Test
    fun `isUnreadableAudio returns true for IllegalArgumentException with no message`() {
        // setDataSource(null) throws an IllegalArgumentException with no message.
        assertTrue(isUnreadableAudio(IllegalArgumentException()))
    }

    @Test
    fun `isUnreadableAudio returns true for empty-message IllegalStateException from setDataSource`() {
        // The native layer throws an empty-message IllegalStateException when no
        // retriever is available (e.g. the media extractor service is unreachable).
        assertTrue(isUnreadableAudio(IllegalStateException()))
    }

    @Test
    fun `isUnreadableAudio returns true for any IllegalStateException regardless of message`() {
        assertTrue(isUnreadableAudio(IllegalStateException("some other wording")))
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
