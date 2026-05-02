package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypeUtilTest {

    @Test
    fun `isImage returns true for image mime types`() {
        assertTrue(MimeTypeUtil.isImage("image/png"))
        assertTrue(MimeTypeUtil.isImage("image/jpeg"))
        assertTrue(MimeTypeUtil.isImage("image/gif"))
        assertTrue(MimeTypeUtil.isImage("image/webp"))
    }

    @Test
    fun `isImage returns false for non-image mime types`() {
        assertFalse(MimeTypeUtil.isImage("video/mp4"))
        assertFalse(MimeTypeUtil.isImage("audio/mpeg"))
        assertFalse(MimeTypeUtil.isImage("application/pdf"))
        assertFalse(MimeTypeUtil.isImage("text/plain"))
    }

    @Test
    fun `isPdf returns true only for pdf mime type`() {
        assertTrue(MimeTypeUtil.isPdf("application/pdf"))
    }

    @Test
    fun `isPdf returns false for non-pdf mime types`() {
        assertFalse(MimeTypeUtil.isPdf("application/msword"))
        assertFalse(MimeTypeUtil.isPdf("image/png"))
        assertFalse(MimeTypeUtil.isPdf("text/plain"))
    }

    @Test
    fun `isAudio returns true for audio mime types`() {
        assertTrue(MimeTypeUtil.isAudio("audio/mpeg"))
        assertTrue(MimeTypeUtil.isAudio("audio/wav"))
        assertTrue(MimeTypeUtil.isAudio("audio/ogg"))
        assertTrue(MimeTypeUtil.isAudio("audio/flac"))
    }

    @Test
    fun `isAudio returns false for non-audio mime types`() {
        assertFalse(MimeTypeUtil.isAudio("video/mp4"))
        assertFalse(MimeTypeUtil.isAudio("image/png"))
        assertFalse(MimeTypeUtil.isAudio("application/pdf"))
    }

    @Test
    fun `isVideo returns true for video mime types`() {
        assertTrue(MimeTypeUtil.isVideo("video/mp4"))
        assertTrue(MimeTypeUtil.isVideo("video/webm"))
        assertTrue(MimeTypeUtil.isVideo("video/x-matroska"))
        assertTrue(MimeTypeUtil.isVideo("video/quicktime"))
    }

    @Test
    fun `isVideo returns false for non-video mime types`() {
        assertFalse(MimeTypeUtil.isVideo("audio/mpeg"))
        assertFalse(MimeTypeUtil.isVideo("image/png"))
        assertFalse(MimeTypeUtil.isVideo("application/pdf"))
    }
}
