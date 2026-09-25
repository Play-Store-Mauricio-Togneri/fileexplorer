package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRenderSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerSupportTest {

    // ==================== mode ====================

    @Test
    fun `mode matrix across api levels and s extension versions`() {
        // (sdkInt, sExtension) -> expected. -1 is what currentMode passes below API 30, where
        // SdkExtensions does not exist.
        val cases = listOf(
            Triple(24, -1, PdfViewerMode.VIEW_ONLY),
            Triple(28, -1, PdfViewerMode.VIEW_ONLY),
            Triple(30, 0, PdfViewerMode.VIEW_ONLY),
            // PdfRendererPreV is gated on API 31 as well as the extension: an R device reporting a
            // high S extension still has no class to load.
            Triple(30, 13, PdfViewerMode.VIEW_ONLY),
            Triple(31, 12, PdfViewerMode.VIEW_ONLY),
            Triple(31, 13, PdfViewerMode.FULL),
            Triple(34, 13, PdfViewerMode.FULL),
            Triple(35, 0, PdfViewerMode.FULL),
            Triple(36, 0, PdfViewerMode.FULL)
        )
        for ((sdk, extension, expected) in cases) {
            assertEquals("sdk $sdk, extension $extension", expected, PdfViewerSupport.mode(sdk, extension))
        }
    }

    // ==================== isAllowedExternalLink ====================

    @Test
    fun `web and mail links are allowed whatever their case`() {
        assertTrue(PdfViewerSupport.isAllowedExternalLink("https://example.com"))
        assertTrue(PdfViewerSupport.isAllowedExternalLink("http://example.com/a?b=c"))
        assertTrue(PdfViewerSupport.isAllowedExternalLink("mailto:someone@example.com"))
        assertTrue(PdfViewerSupport.isAllowedExternalLink("HTTPS://EXAMPLE.COM"))
        assertTrue(PdfViewerSupport.isAllowedExternalLink("MailTo:someone@example.com"))
    }

    @Test
    fun `every other scheme is dropped`() {
        listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "file:///sdcard/secret.txt",
            "content://com.example.provider/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "tel:123",
            "data:text/html,hi",
            "market://details?id=x"
        ).forEach { url ->
            assertFalse(url, PdfViewerSupport.isAllowedExternalLink(url))
        }
    }

    @Test
    fun `malformed links are dropped`() {
        listOf(
            "",
            "example.com",
            ":no-scheme",
            " https://example.com",
            "https:",
            "https:   ",
            "ht tp://example.com",
            "1http://example.com"
        ).forEach { url ->
            assertFalse("'$url'", PdfViewerSupport.isAllowedExternalLink(url))
        }
    }

    // ==================== parsePageNumber ====================

    @Test
    fun `page numbers within range are accepted`() {
        assertEquals(1, PdfViewerSupport.parsePageNumber("1", 10))
        assertEquals(10, PdfViewerSupport.parsePageNumber("10", 10))
        assertEquals(7, PdfViewerSupport.parsePageNumber(" 7 ", 10))
    }

    @Test
    fun `page numbers outside range or not numbers are rejected`() {
        listOf("0", "11", "-1", "", " ", "abc", "1.5", "99999999999999").forEach { input ->
            assertNull("'$input'", PdfViewerSupport.parsePageNumber(input, 10))
        }
        assertNull(PdfViewerSupport.parsePageNumber("1", 0))
    }

    // ==================== renderSize / fitWithin ====================

    @Test
    fun `a page is rendered at the target width with its aspect ratio`() {
        // A4 in points, 1080 px wide.
        assertEquals(PdfRenderSize(1080, 1528), PdfViewerSupport.renderSize(595, 842, 1080, 1f))
    }

    @Test
    fun `zoom multiplies the size and is clamped to the supported range`() {
        assertEquals(PdfRenderSize(400, 400), PdfViewerSupport.renderSize(100, 100, 200, 2f))
        assertEquals(
            PdfViewerSupport.renderSize(100, 100, 200, PdfViewerSupport.MAX_ZOOM),
            PdfViewerSupport.renderSize(100, 100, 200, 100f)
        )
        assertEquals(PdfRenderSize(200, 200), PdfViewerSupport.renderSize(100, 100, 200, 0.1f))
    }

    @Test
    fun `a very tall page is capped at the longest side`() {
        val size = PdfViewerSupport.renderSize(100, 10_000, 1080, 1f)
        // Float rounding may land a pixel short of the cap, never over it.
        assertTrue(size.height in PdfViewerSupport.MAX_BITMAP_SIDE - 1..PdfViewerSupport.MAX_BITMAP_SIDE)
        assertEquals(40, size.width)
    }

    @Test
    fun `a large square page is capped by the pixel budget`() {
        val size = PdfViewerSupport.renderSize(1000, 1000, 4000, 1f)
        assertTrue(size.width.toLong() * size.height <= PdfViewerSupport.MAX_BITMAP_PIXELS)
        assertTrue(size.width <= PdfViewerSupport.MAX_BITMAP_SIDE)
        assertEquals(size.width, size.height)
        // Shrunk only as far as the budget requires.
        assertTrue(size.width > 2800)
    }

    @Test
    fun `degenerate sizes never produce an empty bitmap`() {
        assertEquals(PdfRenderSize(1, 1), PdfViewerSupport.fitWithin(0f, 0f))
        val size = PdfViewerSupport.renderSize(0, 0, 0, 1f)
        assertTrue(size.width >= 1 && size.height >= 1)
    }

    @Test
    fun `an extreme page shape is cut to the tallest one the layout can hold`() {
        assertEquals(
            PdfPageSize(1, PdfViewerSupport.MAX_PAGE_ASPECT),
            PdfViewerSupport.layoutPageSize(PdfPageSize(1, 1000))
        )
        // Ordinary pages, receipts included, are left alone.
        assertEquals(PdfPageSize(595, 842), PdfViewerSupport.layoutPageSize(PdfPageSize(595, 842)))
        assertEquals(PdfPageSize(164, 7200), PdfViewerSupport.layoutPageSize(PdfPageSize(164, 7200)))
    }

    // ==================== cache ====================

    @Test
    fun `cache budget is an eighth of the heap within its bounds`() {
        val mb = 1024L * 1024
        assertEquals(32 * mb, PdfViewerSupport.cacheBudgetBytes(256 * mb))
        assertEquals(8 * mb, PdfViewerSupport.cacheBudgetBytes(32 * mb))
        assertEquals(64 * mb, PdfViewerSupport.cacheBudgetBytes(2048 * mb))
    }

    @Test
    fun `byte count is four bytes per pixel`() {
        assertEquals(4L * 10 * 20, PdfViewerSupport.byteCount(PdfRenderSize(10, 20)))
    }
}
