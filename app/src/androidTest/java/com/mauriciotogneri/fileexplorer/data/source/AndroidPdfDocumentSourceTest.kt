package com.mauriciotogneri.fileexplorer.data.source

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.PdfLink
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * [AndroidPdfDocumentOpener] against the platform renderers, on real files: the three
 * `AndroidPdfDocumentSource` renderers held to the `PdfDocumentSource` contract.
 *
 * Fixtures: the plain documents are written at runtime by [DocumentFixtures.createPdf]. Two are
 * checked in under `androidTest/assets`, because the platform's writer can produce neither links
 * nor encryption:
 *
 * - `pdf_links.pdf` — three 300 x 400 pt pages. Page 1 draws "Jump to page three" and "Visit
 *   website" in Helvetica 18 at baselines y = 312 and y = 212 (PDF space, origin bottom-left), under
 *   two `/Link` annotations: `/Rect [20 300 180 340]` with
 *   `/A << /S /GoTo /D [<page 3> /XYZ 0 400 0] >>`, and `/Rect [20 200 180 240]` with
 *   `/A << /S /URI /URI (https://example.com) >>`. Hand-written objects with a computed xref, then
 *   normalised with `qpdf pdf_links_src.pdf pdf_links.pdf`. The destination is `/XYZ` on purpose:
 *   measured on API 36, `Page.getGotoLinks()` drops a GoTo whose destination is `/Fit` (and a bare
 *   `/Dest` link), so a `/Fit` fixture reports no internal link at all.
 * - `pdf_encrypted_secret.pdf` — the same document, AES-256 encrypted with user and owner
 *   password "secret": `qpdf --encrypt secret secret 256 -- pdf_links.pdf pdf_encrypted_secret.pdf`.
 *
 * FULL-only cases assume on the platform itself — the API level and the S extension read here,
 * never `PdfViewerSupport.mode` — so a broken mode decision fails the viewer tests instead of
 * skipping these.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPdfDocumentSourceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(instrumentation.targetContext.cacheDir, "pdf_source_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // ==================== Every mode ====================

    @Test
    fun plainDocument_reportsPagesAndSizes_inEveryMode() {
        val pdf = DocumentFixtures.createPdf(testDir, pageCount = 3)
        for (mode in PdfViewerMode.entries) {
            val document = opener(mode).open(pdf, null)
            try {
                assertEquals("$mode", 3, document.pageCount)
                assertEquals("$mode", PdfPageSize(200, 200), document.pageSize(2))
            } finally {
                document.close()
            }
        }
    }

    @Test
    fun render_paintsAWhitePageWithInkOnIt() {
        val pdf = DocumentFixtures.createPdf(testDir, pageCount = 1)
        val document = opener(PdfViewerMode.VIEW_ONLY).open(pdf, null)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            document.render(0, bitmap, 2f, 0f, 0f)
        } finally {
            document.close()
        }
        // A fresh bitmap is transparent: an opaque white corner proves the erase ran.
        assertEquals(Color.WHITE, bitmap.getPixel(2, 2))
        assertTrue("the page text must have been drawn", countDark(bitmap) > 0)
    }

    @Test
    fun renderRegion_matchesTheSameAreaOfAWholePageRender() {
        val pdf = DocumentFixtures.createPdf(testDir, pageCount = 1)
        val document = opener(PdfViewerMode.VIEW_ONLY).open(pdf, null)
        val whole = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        // The text sits at x = 20..~80, baseline y = 100 in points: at 4x that is 80..320, ~340..400.
        val region = Bitmap.createBitmap(300, 120, Bitmap.Config.ARGB_8888)
        try {
            document.render(0, whole, 4f, 0f, 0f)
            document.render(0, region, 4f, 60f, 300f)
        } finally {
            document.close()
        }
        val inkInRegion = countDark(region)
        assertTrue("the region must contain the text", inkInRegion > 0)
        var mismatches = 0
        for (y in 0 until region.height) {
            for (x in 0 until region.width) {
                if (isDark(region.getPixel(x, y)) != isDark(whole.getPixel(x + 60, y + 300))) mismatches++
            }
        }
        // Anti-aliasing may round a few edge pixels differently; misplaced ink would differ wholesale.
        assertTrue("$mismatches of $inkInRegion ink pixels misplaced", mismatches < inkInRegion / 10 + 5)
    }

    @Test
    fun corruptFile_throwsIOException() {
        val file = File(testDir, "broken.pdf").apply { writeText("not a pdf at all") }
        for (mode in PdfViewerMode.entries) {
            try {
                opener(mode).open(file, null).close()
                fail("$mode opened a file that is not a PDF")
            } catch (_: IOException) {
            }
        }
    }

    @Test
    fun missingFile_throwsIOException() {
        try {
            opener(PdfViewerMode.VIEW_ONLY).open(File(testDir, "absent.pdf"), null).close()
            fail("opened a file that does not exist")
        } catch (_: IOException) {
        }
    }

    @Test
    fun viewOnly_refusesAnEncryptedDocument_evenWithItsPassword() {
        val pdf = asset("pdf_encrypted_secret.pdf")
        for (password in listOf(null, "secret")) {
            try {
                opener(PdfViewerMode.VIEW_ONLY).open(pdf, password).close()
                fail("view-only opened an encrypted document with password $password")
            } catch (_: SecurityException) {
            }
        }
    }

    @Test
    fun viewOnly_hasNoSearchAndNoLinks() {
        val document = opener(PdfViewerMode.VIEW_ONLY).open(asset("pdf_links.pdf"), null)
        try {
            assertEquals(emptyList<Any>(), document.search(0, "Jump"))
            assertEquals(emptyList<PdfLink>(), document.links(0))
        } finally {
            document.close()
        }
    }

    // ==================== FULL ====================

    @Test
    fun full_encryptedDocument_needsTheRightPassword() {
        assumeFullRendererAvailable()
        val pdf = asset("pdf_encrypted_secret.pdf")
        for (password in listOf(null, "wrong")) {
            try {
                opener(PdfViewerMode.FULL).open(pdf, password).close()
                fail("opened an encrypted document with password $password")
            } catch (_: SecurityException) {
            }
        }
        val document = opener(PdfViewerMode.FULL).open(pdf, "secret")
        try {
            assertEquals(3, document.pageCount)
        } finally {
            document.close()
        }
    }

    @Test
    fun full_search_findsEveryOccurrenceOnItsPage() {
        assumeFullRendererAvailable()
        val pdf = DocumentFixtures.createPdf(testDir, pageCount = 3) { index ->
            if (index == 1) "zebra and zebra" else "okapi"
        }
        val document = opener(PdfViewerMode.FULL).open(pdf, null)
        try {
            assertEquals(0, document.search(0, "zebra").size)
            assertEquals(2, document.search(1, "zebra").size)
            assertEquals(0, document.search(2, "zebra").size)
        } finally {
            document.close()
        }
    }

    @Test
    fun full_searchBounds_useATopLeftOrigin() {
        assumeFullRendererAvailable()
        val document = opener(PdfViewerMode.FULL).open(asset("pdf_links.pdf"), null)
        try {
            val match = document.search(0, "Jump").single().first()
            // Baseline at y = 312 from the bottom of a 400 pt page is 88 pt from the top; the glyphs
            // sit just above it. Bottom-left coordinates would put the rectangle near 312 instead.
            assertTrue("top ${match.top}", match.top in 60f..92f)
            assertTrue("bottom ${match.bottom}", match.bottom in 80f..100f)
            assertTrue("left ${match.left}", abs(match.left - 24f) < 6f)
        } finally {
            document.close()
        }
    }

    @Test
    fun full_links_reportGoToAndExternalTargetsWithTheirAreas() {
        assumeFullRendererAvailable()
        val document = opener(PdfViewerMode.FULL).open(asset("pdf_links.pdf"), null)
        try {
            val links = document.links(0)
            val goTo = links.filterIsInstance<PdfLink.GoTo>().single()
            assertEquals(2, goTo.page)
            val goToArea = goTo.rects.single()
            assertTrue("$goToArea", goToArea.contains(100f, 80f))

            val external = links.filterIsInstance<PdfLink.External>().single()
            assertEquals("https://example.com", external.url)
            assertTrue("${external.rects}", external.rects.single().contains(100f, 180f))

            assertEquals(emptyList<PdfLink>(), document.links(1))
        } finally {
            document.close()
        }
    }

    // ==================== Helpers ====================

    private fun opener(mode: PdfViewerMode) = AndroidPdfDocumentOpener(mode)

    private fun asset(name: String): File =
        DocumentFixtures.copyAsset(instrumentation.context, name, testDir)

    /** The platform's own answer, read independently of the production mode decision. */
    private fun assumeFullRendererAvailable() {
        assumeTrue(
            "no search/link/password-capable renderer on this device",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13)
        )
    }

    private fun isDark(pixel: Int): Boolean =
        Color.alpha(pixel) > 128 && Color.red(pixel) < 128 && Color.green(pixel) < 128 && Color.blue(pixel) < 128

    private fun countDark(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (isDark(bitmap.getPixel(x, y))) count++
            }
        }
        return count
    }
}
