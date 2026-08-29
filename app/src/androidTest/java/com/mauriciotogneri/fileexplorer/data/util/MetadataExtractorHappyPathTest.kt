package com.mauriciotogneri.fileexplorer.data.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The positive control for [MetadataExtractorRobustnessTest].
 *
 * That suite proves an extractor does not crash or destroy data on malformed input — a guarantee
 * `return null` satisfies perfectly. Every assertion here is one an extractor that stopped reading
 * its format would fail: the section it feeds would silently vanish from the Item Info screen and
 * nothing else in the suite would notice.
 *
 * Only CSV, vCard and iCalendar had such a control before; the other nine formats are covered here.
 * The fixtures are built at runtime by [DocumentFixtures] except MP3 and MP4, which need a real
 * encoder and ship in `androidTest/assets`.
 */
@RunWith(AndroidJUnit4::class)
class MetadataExtractorHappyPathTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "extractor_happy_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun pdfExtractor_onRealDocument_reportsThePageCount() {
        val pdf = DocumentFixtures.createPdf(testDir, pageCount = 3)

        val metadata = PdfMetadataExtractor.extract(pdf)

        assertEquals("PdfRenderer should see every page written", 3, metadata?.pageCount)
    }

    @Test
    fun epubExtractor_onRealBook_readsTheOpfMetadata() {
        val epub = DocumentFixtures.createEpub(
            testDir,
            title = "The Fixture",
            creator = "Ada Lovelace",
            publisher = "Fixture Press"
        )

        val metadata = EpubMetadataExtractor.extract(epub)

        assertEquals("The Fixture", metadata?.title)
        assertEquals("Ada Lovelace", metadata?.creator)
        assertEquals("Fixture Press", metadata?.publisher)
        assertEquals("en", metadata?.language)
    }

    @Test
    fun officeExtractor_onRealDocument_readsCoreProperties() {
        val docx = DocumentFixtures.createDocx(
            testDir,
            title = "Quarterly Report",
            creator = "Alan Turing"
        )

        val metadata = OfficeMetadataExtractor.extract(docx)

        assertEquals("Quarterly Report", metadata?.title)
        assertEquals("Alan Turing", metadata?.creator)
    }

    @Test
    fun sqliteExtractor_onRealDatabase_reportsTablesAndRows() {
        val database = DocumentFixtures.createSqliteDb(
            testDir,
            tables = listOf("notes", "authors"),
            rowsPerTable = 2
        )

        val metadata = SqliteMetadataExtractor.extract(database)

        assertEquals(2, metadata?.tableCount)
        // Long, not Int: a nullable actual binds assertEquals(Object, Object), where Integer(4)
        // does not equal Long(4) and the failure reads "expected:<4> but was:<4>".
        assertEquals(4L, metadata?.totalRowCount)
        assertTrue(
            "Table names should be reported, got ${metadata?.tableNames}",
            metadata?.tableNames?.containsAll(listOf("notes", "authors")) == true
        )
    }

    @Test
    fun zipExtractor_onRealArchive_countsEntries() {
        val zip = FileFixtures.createZip(
            testDir,
            "archive.zip",
            mapOf("one.txt" to "first", "two.txt" to "second")
        )

        val metadata = ZipMetadataExtractor.extract(zip)

        assertEquals(2, metadata?.entryCount)
        assertNotNull("A non-empty archive should report an uncompressed size", metadata?.uncompressedSize)
    }

    @Test
    fun imageExtractor_onRealJpeg_readsExifTags() {
        val jpeg = DocumentFixtures.createJpegWithExif(
            testDir,
            width = 80,
            height = 60,
            cameraMake = "Fixture Optics",
            cameraModel = "FX-1"
        )

        val metadata = ImageMetadataExtractor.extract(jpeg)

        assertEquals("Fixture Optics", metadata?.cameraMake)
        assertEquals("FX-1", metadata?.cameraModel)
        assertEquals(80, metadata?.width)
        assertEquals(60, metadata?.height)
    }

    @Test
    fun audioExtractor_onRealMp3_readsTagsAndDuration() {
        val mp3 = DocumentFixtures.copyAsset(testContext, "sample_audio.mp3", testDir)

        val metadata = AudioMetadataExtractor.extract(mp3)

        assertEquals("Fixture Track", metadata?.title)
        assertEquals("Fixture Artist", metadata?.artist)
        assertEquals("Fixture Album", metadata?.album)
        assertTrue("A real MP3 should report a duration, got ${metadata?.duration}", (metadata?.duration ?: 0) > 0)
    }

    @Test
    fun videoExtractor_onRealMp4_readsDimensionsAndDuration() {
        val mp4 = DocumentFixtures.copyAsset(testContext, "sample_video.mp4", testDir)

        val metadata = VideoMetadataExtractor.extract(mp4)

        assertEquals(160, metadata?.width)
        assertEquals(120, metadata?.height)
        assertTrue("A real MP4 should report a duration, got ${metadata?.duration}", (metadata?.duration ?: 0) > 0)
    }

    /**
     * The APK the test process is running from — the one file guaranteed to be a valid archive on
     * any device the suite runs on.
     */
    @Test
    fun apkExtractor_onRealApk_readsThePackageName() {
        val apk = File(testDir, "real.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk)

        val metadata = ApkMetadataExtractor.extract(context, apk)

        assertEquals(context.packageName, metadata?.packageName)
    }
}
