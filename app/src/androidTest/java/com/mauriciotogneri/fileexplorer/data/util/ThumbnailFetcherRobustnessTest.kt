package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Size
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
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
 * [ThumbnailDiskCacheWiringTest] covers the cache wiring. This covers what happens when the bytes
 * are wrong — plus, at the end, one well-formed file per fetcher. Those five are what stop the
 * rest of the file passing against fetchers rewritten to return null: only the APK had such a
 * control before, so a PDF, EPUB, audio or video thumbnail could have stopped rendering app-wide
 * with this suite still green.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailFetcherRobustnessTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The test APK, which is where the `androidTest/assets` fixtures live. */
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context

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

    /**
     * A directory named like a media file. `Factory.create` only checks `exists()`/`canRead()` and
     * then asks [MimeTypeUtil] for a type, which is name-based — so `Album.mp4` routes a folder
     * straight into `MediaMetadataRetriever.setDataSource`, `ZipFile(...)` or
     * `ParcelFileDescriptor.open`. [MetadataExtractorRobustnessTest] pins this for the extractors;
     * the fetchers had no equivalent.
     *
     * The `is ErrorResult` half is weak on its own and is not what this is for: Coil catches every
     * throwable a fetcher raises, and when one returns null it falls through to the built-in file
     * fetcher, whose own failure to open a directory satisfies that check regardless. The clause
     * with teeth is the last one — that probing a directory did not remove it.
     */
    @Test
    fun everyFetcher_onDirectory_failsWithoutThrowing() {
        fetcherExtensions.forEach { extension ->
            val directory = File(testDir, "folder.$extension").apply { mkdirs() }

            val result = try {
                load(directory)
            } catch (error: Throwable) {
                throw AssertionError("Loading a directory named .$extension threw: $error", error)
            }

            assertTrue("A directory should not produce a thumbnail", result is ErrorResult)
            assertTrue("Thumbnail fetch deleted the directory", directory.isDirectory)
        }
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

    /**
     * The other four fetchers, each against a well-formed file of its own format. Without these the
     * suite above would pass just as happily against fetchers rewritten to return null, and the
     * PDF, EPUB, audio and video thumbnails would quietly disappear from every list in the app.
     */
    @Test
    fun pdfFetcher_onRealDocument_producesAThumbnail() {
        assertProducesThumbnail(DocumentFixtures.createPdf(testDir, pageCount = 1), ThumbnailFileType.PDF)
    }

    @Test
    fun epubFetcher_onRealBookWithCover_producesAThumbnail() {
        assertProducesThumbnail(DocumentFixtures.createEpub(testDir), ThumbnailFileType.EPUB)
    }

    @Test
    fun audioFetcher_onRealMp3WithCoverArt_producesAThumbnail() {
        val mp3 = DocumentFixtures.copyAsset(testContext, "sample_audio.mp3", testDir)

        assertProducesThumbnail(mp3, ThumbnailFileType.AUDIO)
    }

    @Test
    fun videoFetcher_onRealMp4_producesAThumbnail() {
        val mp4 = DocumentFixtures.copyAsset(testContext, "sample_video.mp4", testDir)

        assertProducesThumbnail(mp4, ThumbnailFileType.VIDEO)
    }

    /** Loads [file], requires a real bitmap back, and clears the entry it just wrote to the cache. */
    private fun assertProducesThumbnail(file: File, type: String) {
        val result = load(file)

        assertTrue(
            "A valid ${file.extension} should produce a thumbnail, got ${(result as? ErrorResult)?.throwable}",
            result is SuccessResult
        )
        AppImageLoader.thumbnails(context).diskCache?.remove(
            thumbnailDiskCacheKey(type, file.absolutePath, file.lastModified())
        )
    }

    private companion object {
        val SIZE = Size(120, 120)
    }
}
