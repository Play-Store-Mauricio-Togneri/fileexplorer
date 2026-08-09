package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The safety contract for the five thumbnail fetchers (APK, audio, EPUB, PDF, video), driven end to
 * end through the real [AppImageLoader] so the routing that picks a fetcher is exercised too.
 *
 * Fetchers decode untrusted bytes with platform decoders — `PdfRenderer`, `MediaMetadataRetriever`,
 * `AssetManager` — that throw a wide variety of unchecked exceptions on malformed input. A fetcher
 * that lets one escape crashes image loading for the whole list, not just the one row, so the
 * requirement is that a bad file yields an [ErrorResult] rather than a thrown exception, and that
 * the file itself is left untouched.
 *
 * [ThumbnailDiskCacheWiringTest] covers the happy path and the cache wiring; this covers what
 * happens when the bytes are wrong.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailFetcherRobustnessTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testDir: File

    /** Extensions that route to a dedicated fetcher, one per fetcher plus common aliases. */
    private val fetcherExtensions = listOf("apk", "mp3", "m4a", "flac", "epub", "pdf", "mp4", "mkv", "webm")

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "thumb_robustness_${System.nanoTime()}").apply { mkdirs() }
        AppImageLoader.thumbnails(context).memoryCache?.clear()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    private fun load(file: File): ImageResult = runBlocking {
        AppImageLoader.thumbnails(context).execute(
            ImageRequest.Builder(context).data(file).size(SIZE).build()
        )
    }

    /**
     * Coil catches a fetcher's exception and reports it as an [ErrorResult], so "did not throw" is
     * not enough on its own — a fetcher that lets an unexpected type escape still surfaces here,
     * and either way the file must be intact afterwards.
     */
    private fun assertSafeFailure(file: File, expectedBytes: ByteArray) {
        val result = try {
            load(file)
        } catch (error: Throwable) {
            throw AssertionError("Loading ${file.name} threw instead of failing cleanly: $error", error)
        }

        assertTrue(
            "Malformed ${file.extension} should not produce a thumbnail",
            result is ErrorResult
        )
        assertTrue("Thumbnail fetch deleted ${file.name}", file.exists())
        assertEquals(
            "Thumbnail fetch changed the size of ${file.name}",
            expectedBytes.size.toLong(),
            file.length()
        )
        assertTrue(
            "Thumbnail fetch rewrote ${file.name}",
            file.readBytes().contentEquals(expectedBytes)
        )
    }

    private fun write(name: String, bytes: ByteArray): File =
        File(testDir, name).apply { writeBytes(bytes) }

    // ==================== Malformed input ====================

    @Test
    fun everyFetcher_onWrongMagicBytes_failsWithoutThrowing() {
        val garbage = "not a media container, just text a user renamed".toByteArray()

        fetcherExtensions.forEach { extension ->
            assertSafeFailure(write("garbage.$extension", garbage), garbage)
        }
    }

    @Test
    fun everyFetcher_onEmptyFile_failsWithoutThrowing() {
        val empty = ByteArray(0)

        fetcherExtensions.forEach { extension ->
            assertSafeFailure(write("empty.$extension", empty), empty)
        }
    }

    /** An interrupted download: a plausible header with nothing behind it. */
    @Test
    fun everyFetcher_onTruncatedContainer_failsWithoutThrowing() {
        // PK\x03\x04 opens a ZIP, which is what an APK, an EPUB and an Office file all are.
        val truncated = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00)

        fetcherExtensions.forEach { extension ->
            assertSafeFailure(write("truncated.$extension", truncated), truncated)
        }
    }

    /** A PDF header over no page data — the input `PdfRenderer` is documented to reject. */
    @Test
    fun pdfFetcher_onHeaderWithoutPages_failsWithoutThrowing() {
        val header = "%PDF-1.7\n%âãÏÓ\n".toByteArray()

        assertSafeFailure(write("headeronly.pdf", header), header)
    }

    /** An encrypted PDF cannot be rendered; that is expected, not a crash. */
    @Test
    fun pdfFetcher_onEncryptedPdf_failsWithoutThrowing() {
        val encrypted = ("%PDF-1.7\n" +
            "1 0 obj\n<< /Filter /Standard /V 2 /R 3 /Length 128 >>\nendobj\n" +
            "trailer\n<< /Encrypt 1 0 R >>\n%%EOF").toByteArray()

        assertSafeFailure(write("encrypted.pdf", encrypted), encrypted)
    }

    @Test
    fun everyFetcher_onMissingFile_failsWithoutThrowing() {
        fetcherExtensions.forEach { extension ->
            val absent = File(testDir, "absent.$extension")
            val result = try {
                load(absent)
            } catch (error: Throwable) {
                throw AssertionError("Loading a missing .$extension threw: $error", error)
            }
            assertTrue("A missing file should not produce a thumbnail", result is ErrorResult)
        }
    }

    // ==================== The happy path still works ====================

    /**
     * The control for everything above: a real APK — the app's own, which is on disk because the
     * app under test is installed — still yields a thumbnail. Without this, the robustness tests
     * would also pass against a fetcher that had been broken into returning null unconditionally.
     */
    @Test
    fun apkFetcher_onRealArchive_stillProducesAThumbnail() {
        val apk = File(testDir, "real.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk)

        val result = load(apk)

        assertTrue(
            "A valid APK should still produce a thumbnail",
            result is SuccessResult
        )
        AppImageLoader.thumbnails(context).diskCache?.remove(
            thumbnailDiskCacheKey(ThumbnailFileType.APK, apk.absolutePath, apk.lastModified())
        )
    }

    private companion object {
        val SIZE = Size(120, 120)
    }
}
