package com.mauriciotogneri.fileexplorer.data.util

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaErrorsTest {

    @Test
    fun `a missing or unreadable file is unplayable`() {
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE))
    }

    @Test
    fun `a malformed or unknown container is unplayable`() {
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
    }

    @Test
    fun `a format the device cannot decode is unplayable`() {
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES))
        assertTrue(isUnplayableMedia(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
    }

    @Test
    fun `failures that may be bugs are reported`() {
        assertFalse(isUnplayableMedia(PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertFalse(isUnplayableMedia(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertFalse(isUnplayableMedia(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(isUnplayableMedia(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertFalse(isUnplayableMedia(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK))
    }
}
