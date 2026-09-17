package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

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
 *
 * Four contracts beyond "it did not throw" are pinned alongside that, each because its absence let
 * a catastrophic change stay invisible:
 *  - the file is byte-identical after a **successful** extraction too, not only after a failed one.
 *    The success path is the one that opens descriptors and hands the file to a decoder.
 *  - an expected corruption files **no** non-fatal. Every fetcher suppresses its own decoder's
 *    failure through an `isUnreadableX` guard, and with that guard inverted or dropped every
 *    renamed or truncated media file on every device would report.
 *  - each extension in [fetcherExtensions] is claimed by its **own** fetcher. Coil's built-in file
 *    fetcher produces the same [ErrorResult] for a malformed file, so the failures above cannot
 *    tell a routed request from an unrouted one.
 *  - the framework's own resources for an archive are **still usable** once the fetch that read
 *    them has returned. Alone among the five, the APK fetcher borrows a process-wide object the
 *    framework caches rather than opening one of its own, and releasing it is damage every other
 *    test here reports as success.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailFetcherRobustnessTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The test APK, which is where the `androidTest/assets` fixtures live. */
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context

    private lateinit var testDir: File

    /** Extensions that route to a dedicated fetcher, one per fetcher plus common aliases. */
    private val fetcherExtensions = listOf("apk", "mp3", "m4a", "flac", "epub", "pdf", "mp4", "mkv", "webm")

    /**
     * The fetcher each of [fetcherExtensions] has to be handed to. An extension added to the list
     * above without an entry here fails [everyFetcherExtension_routesToItsDedicatedFetcher] rather
     * than being skipped by it.
     */
    private val expectedFetchers: Map<String, Class<*>> = mapOf(
        "apk" to ApkThumbnailFetcher::class.java,
        "mp3" to AudioThumbnailFetcher::class.java,
        "m4a" to AudioThumbnailFetcher::class.java,
        "flac" to AudioThumbnailFetcher::class.java,
        "epub" to EpubThumbnailFetcher::class.java,
        "pdf" to PdfThumbnailFetcher::class.java,
        "mp4" to VideoThumbnailFetcher::class.java,
        "mkv" to VideoThumbnailFetcher::class.java,
        "webm" to VideoThumbnailFetcher::class.java
    )

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
        fetcherExtensions.forEach { extension ->
            assertSafeFailure(write("garbage.$extension", WRONG_MAGIC_BYTES), WRONG_MAGIC_BYTES)
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

            var result: ImageResult? = null
            // A folder the user named "album.mp4" is ordinary, not a fault, so it must also file
            // nothing. EpubThumbnailFetcher.Factory gates on isFile() for exactly this reason:
            // ZipFile(directory) raises FileNotFoundException, which isUnreadableZip() does not
            // match — and must not start matching, since it is what a file removed mid-read raises.
            // Without the gate every such folder in a listing filed a non-fatal.
            val reports = errorReportsWhile("a directory named .$extension") {
                result = try {
                    load(directory)
                } catch (error: Throwable) {
                    throw AssertionError("Loading a directory named .$extension threw: $error", error)
                }
            }

            assertTrue("A directory should not produce a thumbnail", result is ErrorResult)
            assertTrue("Thumbnail fetch deleted the directory", directory.isDirectory)
            assertTrue(
                "A directory named .$extension filed a non-fatal: $reports",
                reports.isEmpty()
            )
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

    // ==================== Expected corruption is not reported ====================

    /**
     * A renamed text file, which is the commonest malformed input there is, must reach Crashlytics
     * as nothing at all.
     *
     * Each fetcher's catch block suppresses its own decoder's failure through an `isUnreadableX`
     * guard ([isUnreadablePdf] and friends, unit-tested on their own but never at the call site).
     * Invert or delete one and every corrupt, truncated or renamed media file on every device files
     * a non-fatal — the production flood CLAUDE.md forbids — while the rest of this file stays green,
     * because an [ErrorResult] is what it asserts either way.
     */
    @Test
    fun everyFetcher_onWrongMagicBytes_filesNoNonFatal() {
        fetcherExtensions.forEach { extension ->
            val file = write("unreported.$extension", WRONG_MAGIC_BYTES)

            val reports = errorReportsWhile(".$extension") {
                assertSafeFailure(file, WRONG_MAGIC_BYTES)
            }

            assertTrue(
                "A renamed text file named .$extension filed a non-fatal: $reports",
                reports.isEmpty()
            )
        }
    }

    /**
     * The control for the test above, which asserts an absence — and an absence is also what a
     * broken observation channel reports.
     *
     * It files a report directly rather than through a fetcher because no input these fetchers can
     * be handed produces a failure they are *supposed* to report: `isUnreadablePdf`,
     * `isUnreadableAudio`, `isUnreadableVideo`, `isUnreadableApk` and `isUnreadableZip` between them
     * cover every exception their decoders raise over a file the user did not create, and the
     * directory case is closed at the factory rather than in a predicate (see
     * [everyFetcher_onDirectory_failsWithoutThrowing]). So what this control proves is the channel,
     * not a guard: with it green, "no report" above means no report.
     *
     * Filing one is safe here: collection is off on debug builds and emulators, which
     * [FirebaseCollectionTest] guards, so the report goes no further than the log line this reads
     * back.
     */
    @Test
    fun aFiledNonFatal_isVisibleToTheseTests() {
        val reports = errorReportsWhile("the control report") {
            ErrorReporter.warning(IllegalStateException("thumbnail robustness control"), CONTROL_OPERATION)
        }

        assertTrue(
            "A non-fatal filed on purpose was not visible, so the absences asserted above prove " +
                "nothing: $reports",
            reports.any { it.contains(CONTROL_OPERATION) }
        )
    }

    // ==================== The dedicated fetcher is the one selected ====================

    /**
     * Which fetcher each extension is actually routed to, asked of the real loader's own component
     * registry — the same objects a live request walks, in the same order.
     *
     * Nothing else in this file can tell routing apart from the absence of it: a malformed file that
     * no dedicated fetcher claims falls through to Coil's built-in file fetcher, whose own failure
     * to decode it is the same [ErrorResult]. The happy-path tests below cover one extension per
     * fetcher, which left `m4a`, `flac`, `mkv` and `webm` — the four with no well-formed fixture
     * here — with nothing showing they reach a fetcher at all. Narrowing [MimeTypeUtil.isAudio] to
     * `audio/mpeg` would drop FLAC and M4A thumbnails app-wide with the rest of the suite green.
     */
    @Test
    fun everyFetcherExtension_routesToItsDedicatedFetcher() {
        val loader = AppImageLoader.thumbnails(context)
        val options = Options(context = context, size = SIZE)

        fetcherExtensions.forEach { extension ->
            // Routing is settled before a byte is read — the factories check `exists()`/`canRead()`
            // and the file's name — so the content of the probe is irrelevant, only that it is a
            // readable regular file.
            val file = write("routing.$extension", ROUTING_PROBE)
            // Mapped first, because Coil maps the File to a `file://` Uri before consulting any
            // fetcher factory, and the factories only accept that Uri.
            val data = loader.components.map(file, options)

            val fetcher = loader.components.newFetcher(data, options, loader)?.first

            assertEquals(
                "A .$extension (${MimeTypeUtil.getMimeType(file)}) must be claimed by its own " +
                    "fetcher rather than falling through to Coil's",
                expectedFetchers[extension],
                fetcher?.javaClass
            )
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

        assertProducesThumbnail(apk, ThumbnailFileType.APK)
    }

    /**
     * The archive's resources outlive the fetch that read them.
     *
     * `PackageManager.getResourcesForApplication` opens nothing private: `ResourcesManager` keys
     * one `Resources` per archive path, registers it process-wide and hands that same instance to
     * every later caller for the path. A fetcher that closes its `AssetManager` therefore destroys
     * an object the framework still has registered — the entry goes only once its weak reference is
     * *cleared*, so until the next collection any process-level configuration change (a rotation,
     * a dark-mode toggle, a locale change) walks the map and calls `updateConfiguration` on it,
     * killing the process with `AssetManager has been destroyed` on a stack the fetcher's own catch
     * never sees.
     *
     * Nothing else in this file can see that: the fetch itself succeeds, so
     * [apkFetcher_onRealArchive_stillProducesAThumbnail] stays green either way. Holding the vended
     * `Resources` across the fetch is what makes the damage observable here and now — the strong
     * reference keeps the shared instance alive, which is also exactly what a second fetch
     * overlapping on the same archive does.
     */
    @Test
    fun apkFetcher_leavesTheFrameworksResourcesForTheArchiveUsable() {
        val apk = File(testDir, "shared.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk)
        // Parsed with the flags the fetcher itself passes, and pointed at the archive the same way,
        // so the ResourcesKey this builds is the one the fetcher's own lookup resolves to.
        val archiveInfo = requireNotNull(
            context.packageManager
                .getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_ACTIVITIES)
                ?.applicationInfo
        ) { "The app's own APK could not be parsed as an archive" }
        archiveInfo.sourceDir = apk.absolutePath
        archiveInfo.publicSourceDir = apk.absolutePath
        val vended = context.packageManager.getResourcesForApplication(archiveInfo)

        val result = load(apk)

        assertTrue(
            "A real APK should produce a thumbnail, got ${(result as? ErrorResult)?.throwable}",
            result is SuccessResult
        )
        // Any AssetManager call reaches the validity check a destroyed manager throws from, and
        // `getLocales` is one that neither caches nor allocates. Re-reading the icon would not do:
        // `Resources` caches drawables by id, so the second read can be served without the manager
        // being touched at all.
        try {
            vended.assets.locales
        } catch (e: RuntimeException) {
            throw AssertionError(
                "The APK fetcher destroyed the AssetManager the framework vends for this archive " +
                    "and still has registered process-wide: $e",
                e
            )
        }

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

    /**
     * Loads [file], requires a real bitmap back, requires the file itself to be untouched, and
     * clears the entry it just wrote to the cache.
     *
     * The integrity half matters more here than in [assertSafeFailure], not less: this is the only
     * path that reaches a decoder at all, so it is the only one where `ParcelFileDescriptor.open`
     * actually opens a descriptor on the user's file and `MediaMetadataRetriever` actually holds a
     * handle on it. `MODE_READ_WRITE` in place of `MODE_READ_ONLY`, or a "tidy up the temp file"
     * step pointed at the source instead of the scratch copy, truncates or deletes the user's real
     * PDF, MP4, MP3, EPUB or APK — with no undo — and every malformed case above stays green,
     * because none of them get this far.
     */
    private fun assertProducesThumbnail(file: File, type: String) {
        val expectedLength = file.length()
        val expectedDigest = digestOf(file)

        val result = load(file)

        assertTrue(
            "A valid ${file.extension} should produce a thumbnail, got ${(result as? ErrorResult)?.throwable}",
            result is SuccessResult
        )
        assertTrue("Thumbnail extraction deleted the ${file.extension} it read", file.exists())
        assertEquals(
            "Thumbnail extraction changed the size of the ${file.extension} it read",
            expectedLength,
            file.length()
        )
        assertArrayEquals(
            "Thumbnail extraction rewrote the bytes of the ${file.extension} it read",
            expectedDigest,
            digestOf(file)
        )

        AppImageLoader.thumbnails(context).diskCache?.remove(
            thumbnailDiskCacheKey(type, file.absolutePath, file.lastModified())
        )
    }

    /**
     * The file's content as a digest rather than as a copy of itself. [assertSafeFailure] compares
     * the bytes directly, which its fixtures are small enough for; the APK control copies the app's
     * own archive — tens of megabytes — and holding two of those on the instrumentation heap while
     * Coil decodes risks an OutOfMemoryError. A digest over every byte catches a truncation or a
     * rewrite just as exactly.
     */
    private fun digestOf(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DIGEST_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    /**
     * Every non-fatal [ErrorReporter] filed while [block] ran, named by [case] in the failure it
     * raises when that cannot be determined.
     *
     * Crashlytics has nothing to read a report back from and mockk is not on the androidTest
     * classpath, so the `Log.e` a debug build makes in `ErrorReporter.report` is the only place a
     * report is observable from a device test. The window is bounded by markers written to the same
     * buffer from the same process, so logd orders them against anything the fetcher logged, and the
     * dump is retried until the closing marker appears: a dump read before it lands could miss a
     * report that was filed and pass vacuously. [aFiledNonFatal_isVisibleToTheseTests] is what
     * proves the channel carries a report at all.
     */
    private fun errorReportsWhile(case: String, block: () -> Unit): List<String> {
        val nonce = System.nanoTime().toString()
        Log.e(PROBE_TAG, "$WINDOW_OPENED$nonce")
        block()
        Log.e(PROBE_TAG, "$WINDOW_CLOSED$nonce")

        repeat(DUMP_ATTEMPTS) {
            val lines = logcat().split("\n")
            val opened = lines.indexOfFirst { it.contains("$WINDOW_OPENED$nonce") }
            val closed = lines.indexOfFirst { it.contains("$WINDOW_CLOSED$nonce") }
            if (opened >= 0 && closed > opened) {
                return lines.subList(opened + 1, closed).filter { it.contains(REPORTER_TAG) }
            }
            Thread.sleep(DUMP_INTERVAL_MS)
        }

        throw AssertionError(
            "logcat never showed the markers bounding $case, so whether a non-fatal was filed " +
                "cannot be read — and reading nothing must not count as reporting nothing"
        )
    }

    /**
     * The main log buffer, dumped through the shell — which, unlike this process, holds READ_LOGS —
     * and silenced down to the two tags involved by `-s`. The command is tokenised on whitespace
     * rather than run by a shell, so it can carry no quoting, no glob and no pipe.
     */
    private fun logcat(): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("logcat -d -b main -s $REPORTER_TAG:E $PROBE_TAG:E")

        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
            it.readBytes().decodeToString()
        }
    }

    private companion object {
        val SIZE = Size(120, 120)

        /** A text file a user renamed: shared so the two tests driving it cannot drift apart. */
        val WRONG_MAGIC_BYTES = "not a media container, just text a user renamed".toByteArray()

        /** Content the routing probes carry, which nothing reads: routing is decided by name. */
        val ROUTING_PROBE = "routing probe".toByteArray()

        const val DIGEST_BUFFER_BYTES = 64 * 1024

        /** `ErrorReporter`'s own log tag, which is private to it, hence the copy. */
        const val REPORTER_TAG = "ErrorReporter"
        const val PROBE_TAG = "ThumbReportProbe"
        const val WINDOW_OPENED = "opened:"
        const val WINDOW_CLOSED = "closed:"
        const val CONTROL_OPERATION = "thumbnail_report_control"
        const val DUMP_ATTEMPTS = 20
        const val DUMP_INTERVAL_MS = 100L
    }
}
