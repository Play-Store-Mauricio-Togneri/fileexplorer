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
        for (sdk in allApis) {
            assertTrue(MimeTypeUtil.isViewableImage("image/png", "a.png", sdk))
            assertTrue(MimeTypeUtil.isViewableImage("image/jpeg", "a.jpg", sdk))
            assertTrue(MimeTypeUtil.isViewableImage("image/webp", "a.webp", sdk))
            assertTrue(MimeTypeUtil.isViewableImage("image/gif", "a.gif", sdk))
            assertTrue(MimeTypeUtil.isViewableImage("image/bmp", "a.bmp", sdk))
            assertTrue(MimeTypeUtil.isViewableImage("image/svg+xml", "a.svg", sdk))
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
            assertFalse(MimeTypeUtil.isViewableImage("image/tiff", "a.tiff", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("image/tiff", "a.tif", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("image/x-icon", "a.ico", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("image/x-canon-cr2", "a.cr2", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("image/x-nikon-nef", "a.nef", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("image/x-adobe-dng", "a.dng", sdk))
        }
    }

    @Test
    fun `non-image content is not viewable`() {
        for (sdk in allApis) {
            assertFalse(MimeTypeUtil.isViewableImage("text/plain", "a.txt", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("application/pdf", "a.pdf", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("video/mp4", "a.mp4", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("*/*", "archive.zip", sdk))
            assertFalse(MimeTypeUtil.isViewableImage("*/*", "README", sdk))
        }
    }
}
