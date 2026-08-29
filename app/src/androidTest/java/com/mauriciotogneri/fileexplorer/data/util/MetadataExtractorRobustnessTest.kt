package com.mauriciotogneri.fileexplorer.data.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The safety contract every metadata extractor owes: a read-only probe of an untrusted file must
 * never throw and must never touch the file on disk.
 *
 * This class exists because of the defect [SqliteMetadataExtractorTest] guards — the framework's
 * default SQLite error handler *deleted* the user's file when a probe hit corrupt data. That was one
 * extractor; eleven others parse equally untrusted bytes and had no test at all. Every extractor is
 * fed the three inputs a real device produces: a well-formed file, a truncated one (an interrupted
 * download), and one whose extension lies about its contents.
 *
 * What is asserted here is that the failure paths degrade to null instead of crashing or destroying
 * data. The happy paths — the control that stops all of this passing against an extractor rewritten
 * to `return null` unconditionally — live in [MetadataExtractorHappyPathTest].
 *
 * This comment used to claim the happy paths were covered by the `ItemInfo*` screen tests. They are
 * not: those tests build `MetadataFixtures` data objects and never invoke an extractor, so for
 * eight of the twelve there was no positive control anywhere.
 */
@RunWith(AndroidJUnit4::class)
class MetadataExtractorRobustnessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "test_extractors_${System.currentTimeMillis()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    /** Every extractor keyed by name, invoked through a uniform `File -> Any?` probe. */
    private val extractors: Map<String, (File) -> Any?> = mapOf(
        "apk" to { file -> ApkMetadataExtractor.extract(context, file) },
        "audio" to { file -> AudioMetadataExtractor.extract(file) },
        "csv" to { file -> CsvMetadataExtractor.extract(file) },
        "epub" to { file -> EpubMetadataExtractor.extract(file) },
        "icalendar" to { file -> ICalendarMetadataExtractor.extract(file) },
        "image" to { file -> ImageMetadataExtractor.extract(file) },
        "office" to { file -> OfficeMetadataExtractor.extract(file) },
        "pdf" to { file -> PdfMetadataExtractor.extract(file) },
        "sqlite" to { file -> SqliteMetadataExtractor.extract(file) },
        "vcard" to { file -> VCardMetadataExtractor.extract(file) },
        "video" to { file -> VideoMetadataExtractor.extract(file) },
        "zip" to { file -> ZipMetadataExtractor.extract(file) }
    )

    private fun write(name: String, bytes: ByteArray): File =
        File(testDir, name).apply { writeBytes(bytes) }

    /**
     * Runs [probe] and reports the failure rather than letting it escape, so one extractor's crash
     * names itself instead of surfacing as an anonymous stack trace.
     */
    private fun probeSafely(label: String, file: File, probe: (File) -> Any?) {
        try {
            probe(file)
        } catch (error: Throwable) {
            throw AssertionError("$label extractor threw on ${file.name}: $error", error)
        }
    }

    // ==================== Garbage input ====================

    /**
     * A file whose extension promises one format and whose bytes are another is routine: users
     * rename things, and downloads land with a guessed extension.
     */
    @Test
    fun everyExtractor_onWrongMagicBytes_returnsWithoutThrowing() {
        val garbage = "this is plain text, not the format the extension claims".toByteArray()

        extractors.forEach { (label, probe) ->
            val file = write("wrong_magic_$label.bin", garbage)
            probeSafely(label, file, probe)
        }
    }

    /** An interrupted download leaves a valid header over a truncated body. */
    @Test
    fun everyExtractor_onTruncatedFile_returnsWithoutThrowing() {
        // PK\x03\x04 is the ZIP/EPUB/Office header; the entries that should follow are missing.
        val truncatedZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)

        extractors.forEach { (label, probe) ->
            val file = write("truncated_$label.bin", truncatedZip)
            probeSafely(label, file, probe)
        }
    }

    @Test
    fun everyExtractor_onEmptyFile_returnsWithoutThrowing() {
        extractors.forEach { (label, probe) ->
            val file = write("empty_$label.bin", ByteArray(0))
            probeSafely(label, file, probe)
        }
    }

    @Test
    fun everyExtractor_onMissingFile_returnsWithoutThrowing() {
        extractors.forEach { (label, probe) ->
            val file = File(testDir, "absent_$label.bin")
            probeSafely(label, file, probe)
        }
    }

    @Test
    fun everyExtractor_onDirectory_returnsWithoutThrowing() {
        extractors.forEach { (label, probe) ->
            val dir = File(testDir, "dir_$label").apply { mkdirs() }
            probeSafely(label, dir, probe)
        }
    }

    // ==================== The data-loss contract ====================

    /**
     * The regression that motivated this file: a metadata probe must never modify or delete the
     * file it inspects. Content and length are captured before each probe and compared after.
     */
    @Test
    fun everyExtractor_neverModifiesOrDeletesTheProbedFile() {
        val garbage = "definitely not a valid container, but still the user's data".toByteArray()

        extractors.forEach { (label, probe) ->
            val file = write("preserve_$label.bin", garbage)
            val sizeBefore = file.length()

            probeSafely(label, file, probe)

            assertTrue("$label extractor deleted the probed file", file.exists())
            assertEquals("$label extractor changed the file length", sizeBefore, file.length())
            assertTrue(
                "$label extractor changed the file contents",
                file.readBytes().contentEquals(garbage)
            )
        }
    }

    /**
     * The same guarantee for a file whose extension invites the destructive path — a `.db` that is
     * not SQLite is exactly the case that used to wipe user data.
     */
    @Test
    fun everyExtractor_neverDeletesAFileWithAMisleadingExtension() {
        val content = "user data that only looks like a database".toByteArray()
        val extensions = listOf("db", "sqlite", "zip", "epub", "docx", "pdf", "apk", "mp3", "mp4", "jpg")

        extensions.forEach { extension ->
            extractors.forEach { (label, probe) ->
                val file = write("misleading_${label}.$extension", content)

                probeSafely(label, file, probe)

                assertTrue(
                    "$label extractor deleted a .$extension file it could not parse",
                    file.exists()
                )
                assertTrue(
                    "$label extractor rewrote a .$extension file it could not parse",
                    file.readBytes().contentEquals(content)
                )
            }
        }
    }

    // ==================== Well-formed input still parses ====================

    /**
     * A guard that only proves "never throws" would also pass if every extractor returned null
     * unconditionally. This pins that at least the text-based extractors still read real content,
     * so the robustness tests above are measuring something.
     */
    @Test
    fun csvExtractor_onWellFormedFile_reportsRowsAndColumns() {
        val csv = write(
            "data.csv",
            "name,age,city\nada,36,london\nalan,41,cambridge\n".toByteArray()
        )

        val metadata = CsvMetadataExtractor.extract(csv)

        assertEquals("Header plus two data rows", 3, metadata?.rowCount)
        assertEquals(3, metadata?.columnCount)
    }

    @Test
    fun vcardExtractor_onWellFormedFile_countsContacts() {
        val vcard = write(
            "contacts.vcf",
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            TEL:+441234567890
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Alan Turing
            EMAIL:alan@example.com
            END:VCARD
            """.trimIndent().toByteArray()
        )

        val metadata = VCardMetadataExtractor.extract(vcard)

        assertEquals(2, metadata?.contactCount)
        assertEquals(true, metadata?.hasPhoneNumbers)
        assertEquals(true, metadata?.hasEmails)
    }

    @Test
    fun icalendarExtractor_onWellFormedFile_countsEvents() {
        val ics = write(
            "calendar.ics",
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART:20260101T090000Z
            SUMMARY:New year standup
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20260202T090000Z
            SUMMARY:Retro
            END:VEVENT
            END:VCALENDAR
            """.trimIndent().toByteArray()
        )

        val metadata = ICalendarMetadataExtractor.extract(ics)

        assertEquals(2, metadata?.eventCount)
    }
}
