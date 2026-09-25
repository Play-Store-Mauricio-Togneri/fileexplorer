package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypeUtilIsViewableImageTest {

    // SDK levels straddling the format gates; passed explicitly so the predicate stays JVM-pure.
    private val apiPreHeif = 23
    private val apiHeif = 28
    private val apiAvif = 31
    private val allApis = intArrayOf(apiPreHeif, apiHeif, apiAvif)

    @Test
    fun `core formats are viewable on every supported api level`() {
        // Each message names the sdk the loop is on: the formats here are gated by API level
        // elsewhere in this file, so "expected true, was false" without it does not say which level
        // a regression started gating.
        for (sdk in allApis) {
            assertTrue("image/png at sdk $sdk", MimeTypeUtil.isViewableImage("image/png", "a.png", sdk))
            assertTrue("image/jpeg at sdk $sdk", MimeTypeUtil.isViewableImage("image/jpeg", "a.jpg", sdk))
            assertTrue("image/webp at sdk $sdk", MimeTypeUtil.isViewableImage("image/webp", "a.webp", sdk))
            assertTrue("image/gif at sdk $sdk", MimeTypeUtil.isViewableImage("image/gif", "a.gif", sdk))
            assertTrue("image/bmp at sdk $sdk", MimeTypeUtil.isViewableImage("image/bmp", "a.bmp", sdk))
            assertTrue("image/svg+xml at sdk $sdk", MimeTypeUtil.isViewableImage("image/svg+xml", "a.svg", sdk))
        }
    }

    @Test
    fun `extension alone qualifies when the mime type is unknown`() {
        // FileItem.getMimeType returns "*/*" for types Android cannot resolve; the extension set
        // is the fallback signal.
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "photo.png", apiPreHeif))
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "drawing.svg", apiPreHeif))
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "scan.jpeg", apiPreHeif))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "PHOTO.PNG", apiPreHeif))
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "Image.JpG", apiPreHeif))
    }

    @Test
    fun `heif is gated to api 28 and above`() {
        assertFalse(MimeTypeUtil.isViewableImage("image/heic", "a.heic", apiPreHeif))
        assertFalse(MimeTypeUtil.isViewableImage("*/*", "a.heif", apiPreHeif))

        assertTrue(MimeTypeUtil.isViewableImage("image/heic", "a.heic", apiHeif))
        assertTrue(MimeTypeUtil.isViewableImage("image/heif", "a.heif", apiHeif))
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "a.heic", apiAvif))
    }

    @Test
    fun `avif is gated to api 31 and above`() {
        assertFalse(MimeTypeUtil.isViewableImage("image/avif", "a.avif", apiPreHeif))
        assertFalse(MimeTypeUtil.isViewableImage("image/avif", "a.avif", apiHeif))

        assertTrue(MimeTypeUtil.isViewableImage("image/avif", "a.avif", apiAvif))
        assertTrue(MimeTypeUtil.isViewableImage("*/*", "a.avif", apiAvif))
    }

    @Test
    fun `image formats Coil cannot decode are not viewable`() {
        for (sdk in allApis) {
            assertFalse("image/tiff (.tiff) at sdk $sdk", MimeTypeUtil.isViewableImage("image/tiff", "a.tiff", sdk))
            assertFalse("image/tiff (.tif) at sdk $sdk", MimeTypeUtil.isViewableImage("image/tiff", "a.tif", sdk))
            assertFalse("image/x-icon at sdk $sdk", MimeTypeUtil.isViewableImage("image/x-icon", "a.ico", sdk))
            assertFalse("image/x-canon-cr2 at sdk $sdk", MimeTypeUtil.isViewableImage("image/x-canon-cr2", "a.cr2", sdk))
            assertFalse("image/x-nikon-nef at sdk $sdk", MimeTypeUtil.isViewableImage("image/x-nikon-nef", "a.nef", sdk))
            assertFalse("image/x-adobe-dng at sdk $sdk", MimeTypeUtil.isViewableImage("image/x-adobe-dng", "a.dng", sdk))
        }
    }

    @Test
    fun `non-image content is not viewable`() {
        for (sdk in allApis) {
            assertFalse("text/plain at sdk $sdk", MimeTypeUtil.isViewableImage("text/plain", "a.txt", sdk))
            assertFalse("application/pdf at sdk $sdk", MimeTypeUtil.isViewableImage("application/pdf", "a.pdf", sdk))
            assertFalse("video/mp4 at sdk $sdk", MimeTypeUtil.isViewableImage("video/mp4", "a.mp4", sdk))
            assertFalse("archive.zip at sdk $sdk", MimeTypeUtil.isViewableImage("*/*", "archive.zip", sdk))
            assertFalse("README (no extension) at sdk $sdk", MimeTypeUtil.isViewableImage("*/*", "README", sdk))
        }
    }
}
