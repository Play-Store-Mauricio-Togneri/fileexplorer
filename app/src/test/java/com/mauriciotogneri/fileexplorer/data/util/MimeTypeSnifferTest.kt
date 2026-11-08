package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class MimeTypeSnifferTest {

    private val jfif = hex("FF D8 FF E0 00 10") + ascii("JFIF") + hex("00 01")
    private val exif = hex("FF D8 FF E1 00 18") + ascii("Exif") + hex("00 00")
    private val rawJpeg = hex("FF D8 FF DB 00 43 00")
    private val jpegMissingThirdByte = hex("FF D8 00 00")

    private val png = hex("89 50 4E 47 0D 0A 1A 0A 00 00 00 0D") + ascii("IHDR")

    private val gif89a = ascii("GIF89a") + hex("01 00 01 00 80 00 00")
    private val gif87a = ascii("GIF87a") + hex("01 00 01 00 80 00 00")
    private val gif88a = ascii("GIF88a") + hex("01 00")

    private val webp = ascii("RIFF") + hex("24 00 00 00") + ascii("WEBP") + ascii("VP8 ")
    private val wav = ascii("RIFF") + hex("24 08 00 00") + ascii("WAVE") + ascii("fmt ")
    private val avi = ascii("RIFF") + hex("00 10 00 00") + ascii("AVI ") + ascii("LIST")
    private val unknownRiff = ascii("RIFF") + hex("04 00 00 00") + ascii("ABCD")

    private val mp4 = hex("00 00 00 18") + ascii("ftyp") + ascii("isom") + hex("00 00 02 00") +
        ascii("isom") + ascii("mp41")
    private val m4a = hex("00 00 00 1C") + ascii("ftyp") + ascii("M4A ") + hex("00 00 00 00") +
        ascii("M4A ") + ascii("mp42") + ascii("isom")
    private val quickTime = ftyp("qt  ", "qt  ", "isom")
    private val threeGpp = ftyp("3gp4", "isom", "3gp4")
    private val threeGpp2 = ftyp("3g2a", "isom", "3g2a")
    private val heic = hex("00 00 00 18") + ascii("ftyp") + ascii("heic") + hex("00 00 00 00") +
        ascii("mif1") + ascii("heic")
    private val genericHeifWithHeic = ftyp("mif1", "mif1", "heic")
    private val avif = ftyp("avif", "avif", "mif1", "miaf")
    private val genericHeifWithAvif = ftyp("mif1", "avif")
    private val genericHeif = ftyp("mif1", "mif1")
    private val canonCr3 = hex("00 00 00 18") + ascii("ftyp") + ascii("crx ") + hex("00 00 00 01") +
        ascii("crx ") + ascii("isom")
    private val ftypBoxTooSmall = hex("00 00 00 04") + ascii("ftyp") + ascii("isom") +
        hex("00 00 02 00") + ascii("isom") + ascii("mp41")
    private val ftypBoxTooLarge = hex("00 00 10 01") + ascii("ftyp") + ascii("isom") +
        hex("00 00 02 00") + ascii("isom") + ascii("mp41")
    private val ftypAtOffsetZero = ascii("ftyp") + ascii("isom") + hex("00 00 02 00") +
        ascii("isom") + ascii("mp41")

    private val ebmlHeaderStart =
        hex("1A 45 DF A3 9F 42 86 81 01 42 F7 81 01 42 F2 81 04 42 F3 81 08")
    private val webm = ebmlHeaderStart + hex("42 82 84") + ascii("webm")
    private val matroska = ebmlHeaderStart + hex("42 82 88") + ascii("matroska")
    private val ebmlWithoutDocType = ebmlHeaderStart

    private val oggVorbis = oggPage(hex("01") + ascii("vorbis"))
    private val oggOpus = oggPage(ascii("OpusHead"))
    private val oggTheora = oggPage(hex("80") + ascii("theora"))
    private val oggSkeleton = oggPage(ascii("fishead") + hex("00"))
    private val oggBadVersion = ascii("OggS") + hex("01 02") + ByteArray(20) + hex("01 1E 01") +
        ascii("vorbis")

    private val flac = ascii("fLaC") + hex("00 00 00 22")

    private val id3Tag = ascii("ID3") + hex("04 00 00 00 00 00 0A") + ByteArray(10)
    private val id3ThenFlac = id3Tag + ascii("fLaC")
    private val id3WithFooterThenFlac = ascii("ID3") + hex("04 00 10 00 00 00 0A") + ByteArray(10) +
        ascii("3DI") + hex("04 00 10 00 00 00 0A") + ascii("fLaC")
    private val id3BadSize = ascii("ID3") + hex("03 00 00 80 00 00 00")

    private val pdf = ascii("%PDF-1.7") + hex("0A") + ascii("%") + hex("E2 E3 CF D3 0A")
    private val pdfAfterBlankLine = hex("0D 0A") + ascii("%PDF-1.4") + hex("0A")
    private val pdfMarkerMidLine = ascii("see %PDF-1.4 header") + hex("0A")
    private val pdfMarkerPastWindow = ascii("a".repeat(1100)) + hex("0A") + ascii("%PDF-1.4")

    private val zipLocalHeader = hex("50 4B 03 04 14 00 00 00 08 00") + ByteArray(16) +
        hex("05 00 00 00") + ascii("a.txt")
    private val zipCutShort = hex("50 4B 00")

    private val helloWorld = ascii("hello world") + hex("0A")
    private val crlfAndTab = ascii("README") + hex("0D 0A 09") + ascii("tabbed")
    private val ansiColouredLog =
        hex("1B") + ascii("[31mERROR") + hex("1B") + ascii("[0m") + hex("0A")
    private val multiByteUtf8 = "naïve café — 日本語 🙂".toByteArray(Charsets.UTF_8)
    private val utf8Bom = hex("EF BB BF") + ascii("hi")
    private val nulInText = ascii("he") + hex("00") + ascii("lo")
    private val latin1 = ascii("caf") + hex("E9") + ascii(" au lait")
    private val cutMultiByteCharacter = ascii("a".repeat(4095)) + hex("E6")
    private val controlCharacters = hex(List(20) { "01 02 03 04" }.joinToString(" ")) + ascii("abc")
    private val onePercentControls = ascii("a".repeat(99)) + hex("01")
    private val twoPercentControls = ascii("a".repeat(98)) + hex("01 01")
    private val empty = ByteArray(0)

    private val allSniffFixtures = listOf(
        jfif, exif, rawJpeg, jpegMissingThirdByte, png, png.copyOf(7),
        gif89a, gif87a, gif88a, webp, wav, avi, unknownRiff,
        mp4, m4a, quickTime, threeGpp, threeGpp2, heic, genericHeifWithHeic, avif,
        genericHeifWithAvif, genericHeif, canonCr3, ftypBoxTooSmall, ftypBoxTooLarge,
        ftypAtOffsetZero, webm, matroska, ebmlWithoutDocType,
        oggVorbis, oggOpus, oggTheora, oggSkeleton, oggBadVersion, flac,
        id3Tag, id3ThenFlac, id3WithFooterThenFlac, id3BadSize,
        pdf, pdfAfterBlankLine, pdfMarkerMidLine, pdfMarkerPastWindow, zipLocalHeader, zipCutShort,
        helloWorld, crlfAndTab, ansiColouredLog, multiByteUtf8, utf8Bom, nulInText, latin1,
        cutMultiByteCharacter, controlCharacters, onePercentControls, twoPercentControls, empty
    )

    private val epubEntries = listOf("mimetype", "META-INF/container.xml", "OEBPS/content.opf")
    private val odfEntries = listOf("mimetype", "content.xml", "META-INF/manifest.xml")
    private val docxEntries = listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")
    private val xlsxEntries = listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml")
    private val pptxEntries = listOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml")
    private val contentTypesOnly = listOf("[Content_Types].xml")
    private val apkEntries =
        listOf("AndroidManifest.xml", "classes.dex", "resources.arsc", "META-INF/MANIFEST.MF")
    private val apkWithoutResources = listOf("AndroidManifest.xml", "classes.dex")
    private val apkWithoutCode = listOf("AndroidManifest.xml", "resources.arsc")
    private val aarEntries = listOf("AndroidManifest.xml", "classes.jar", "R.txt")
    private val jarEntries = listOf("META-INF/MANIFEST.MF", "com/example/Main.class")
    private val plainZipEntries = listOf("a.txt", "b/c.png")

    private val odfTypes = listOf(
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/vnd.oasis.opendocument.graphics"
    )

    private val allZipFixtures: List<Pair<List<String>, String?>> = listOf(
        epubEntries to "application/epub+zip",
        epubEntries to "application/epub+zip\n",
        odfEntries to "text/x-made-up",
        odfEntries to "a".repeat(200),
        docxEntries to null, xlsxEntries to null, pptxEntries to null, contentTypesOnly to null,
        apkEntries to null, apkWithoutResources to null, apkWithoutCode to null,
        aarEntries to null, jarEntries to null, plainZipEntries to null
    ) + odfTypes.map { odfEntries to it }

    @Test
    fun `sniff recognises a JFIF JPEG`() {
        assertEquals("image/jpeg", sniff(jfif))
    }

    @Test
    fun `sniff recognises EXIF and raw JPEG markers`() {
        assertEquals("image/jpeg", sniff(exif))
        assertEquals("image/jpeg", sniff(rawJpeg))
    }

    @Test
    fun `sniff rejects a JPEG marker missing its third byte`() {
        assertNull(sniff(jpegMissingThirdByte))
    }

    @Test
    fun `sniff recognises PNG`() {
        assertEquals("image/png", sniff(png))
    }

    @Test
    fun `sniff rejects a PNG signature cut to seven bytes`() {
        assertNull(sniff(png.copyOf(7)))
        // The same cut through length alone: bytes past it are never read.
        assertNull(MimeTypeSniffer.sniff(png, 7, reachedEof = true))
    }

    @Test
    fun `sniff recognises GIF89a and GIF87a`() {
        assertEquals("image/gif", sniff(gif89a))
        assertEquals("image/gif", sniff(gif87a))
    }

    @Test
    fun `sniff rejects an unknown GIF version`() {
        assertNull(sniff(gif88a))
    }

    @Test
    fun `sniff recognises WebP`() {
        assertEquals("image/webp", sniff(webp))
    }

    @Test
    fun `sniff tells WAV and AVI apart by the RIFF form type`() {
        assertEquals("audio/x-wav", sniff(wav))
        assertEquals("video/x-msvideo", sniff(avi))
    }

    @Test
    fun `sniff rejects an unknown RIFF form type`() {
        assertNull(sniff(unknownRiff))
    }

    @Test
    fun `sniff recognises an isom MP4`() {
        assertEquals("video/mp4", sniff(mp4))
    }

    @Test
    fun `sniff recognises M4A as audio`() {
        assertEquals("audio/mp4", sniff(m4a))
    }

    @Test
    fun `sniff recognises QuickTime, 3GP and 3G2 by major brand`() {
        assertEquals("video/quicktime", sniff(quickTime))
        assertEquals("video/3gpp", sniff(threeGpp))
        assertEquals("video/3gpp2", sniff(threeGpp2))
    }

    @Test
    fun `sniff recognises a HEIC major brand as an image`() {
        assertEquals("image/heic", sniff(heic))
    }

    @Test
    fun `sniff reads HEIC from the compatible brands of a generic HEIF brand`() {
        assertEquals("image/heic", sniff(genericHeifWithHeic))
    }

    @Test
    fun `sniff recognises AVIF as a major or a compatible brand`() {
        assertEquals("image/avif", sniff(avif))
        assertEquals("image/avif", sniff(genericHeifWithAvif))
    }

    @Test
    fun `sniff falls back to HEIF for a generic brand naming no codec`() {
        assertEquals("image/heif", sniff(genericHeif))
    }

    @Test
    fun `sniff does not take a Canon CR3 for an MP4 despite its isom compatible brand`() {
        assertNull(sniff(canonCr3))
    }

    @Test
    fun `sniff rejects an ftyp box with an implausible size or offset`() {
        assertNull(sniff(ftypBoxTooSmall))
        assertNull(sniff(ftypBoxTooLarge))
        assertNull(sniff(ftypAtOffsetZero))
    }

    @Test
    fun `sniff recognises WebM from the EBML DocType`() {
        assertEquals("video/webm", sniff(webm))
    }

    @Test
    fun `sniff calls any other EBML file Matroska`() {
        assertEquals("video/x-matroska", sniff(matroska))
        assertEquals("video/x-matroska", sniff(ebmlWithoutDocType))
    }

    @Test
    fun `sniff recognises Ogg Vorbis as audio`() {
        assertEquals("audio/ogg", sniff(oggVorbis))
    }

    @Test
    fun `sniff reads the first Ogg packet to tell audio from video`() {
        assertEquals("audio/ogg", sniff(oggOpus))
        assertEquals("video/ogg", sniff(oggTheora))
        assertEquals("video/ogg", sniff(oggSkeleton))
    }

    @Test
    fun `sniff rejects an Ogg page with an unknown version`() {
        assertNull(sniff(oggBadVersion))
    }

    @Test
    fun `sniff recognises FLAC`() {
        assertEquals("audio/flac", sniff(flac))
    }

    @Test
    fun `sniff recognises an ID3v2 tag as MP3`() {
        assertEquals("audio/mpeg", sniff(id3Tag))
    }

    @Test
    fun `sniff recognises FLAC right after an ID3v2 tag, footer included`() {
        assertEquals("audio/flac", sniff(id3ThenFlac))
        assertEquals("audio/flac", sniff(id3WithFooterThenFlac))
    }

    @Test
    fun `sniff rejects an ID3 header whose size is not syncsafe`() {
        assertNull(sniff(id3BadSize))
    }

    @Test
    fun `sniff recognises a PDF header at the start`() {
        assertEquals("application/pdf", sniff(pdf))
    }

    @Test
    fun `sniff recognises a PDF header at a later line start`() {
        assertEquals("application/pdf", sniff(pdfAfterBlankLine))
    }

    @Test
    fun `sniff treats a PDF marker in the middle of a line as text`() {
        assertEquals("text/plain", sniff(pdfMarkerMidLine))
    }

    @Test
    fun `sniff treats a PDF marker past the first kilobyte as text`() {
        assertEquals("text/plain", sniff(pdfMarkerPastWindow))
    }

    @Test
    fun `sniff recognises a ZIP local file header`() {
        assertEquals("application/zip", sniff(zipLocalHeader))
    }

    @Test
    fun `sniff rejects a ZIP signature cut short`() {
        assertNull(sniff(zipCutShort))
    }

    @Test
    fun `sniff recognises plain text with line endings, tabs and ANSI colours`() {
        assertEquals("text/plain", sniff(helloWorld))
        assertEquals("text/plain", sniff(crlfAndTab))
        assertEquals("text/plain", sniff(ansiColouredLog))
    }

    @Test
    fun `sniff recognises multi-byte UTF-8 as text`() {
        assertEquals("text/plain", sniff(multiByteUtf8))
    }

    @Test
    fun `sniff skips a UTF-8 byte order mark`() {
        assertEquals("text/plain", sniff(utf8Bom))
    }

    @Test
    fun `sniff rejects text containing a NUL byte`() {
        assertNull(sniff(nulInText))
    }

    @Test
    fun `sniff rejects Latin-1 text`() {
        assertNull(sniff(latin1))
    }

    @Test
    fun `sniff accepts a character cut by the read cap but not one cut by the end of the file`() {
        assertEquals("text/plain", sniff(cutMultiByteCharacter, reachedEof = false))
        assertNull(sniff(cutMultiByteCharacter, reachedEof = true))
    }

    @Test
    fun `sniff rejects text with more than one percent control characters`() {
        assertNull(sniff(controlCharacters))
        assertEquals("text/plain", sniff(onePercentControls))
        assertNull(sniff(twoPercentControls))
    }

    @Test
    fun `sniff finds nothing in an empty prefix`() {
        assertNull(sniff(empty))
    }

    @Test
    fun `every type sniff and zipType return is in the analytics vocabulary`() {
        val sniffed = allSniffFixtures.flatMap { listOf(sniff(it, true), sniff(it, false)) }
        val zipTyped = allZipFixtures.map { (names, mimetype) -> zip(names, mimetype) }
        val results = (sniffed + zipTyped).filterNotNull()

        assertFalse(results.isEmpty())
        results.forEach { assertTrue(it, it in MimeTypeSniffer.TYPES) }
    }

    @Test
    fun `zipType recognises EPUB from its mimetype entry and returns its own constant`() {
        val declared = StringBuilder("application/epub+zip").toString()

        val type = zip(epubEntries, declared)

        assertEquals("application/epub+zip", type)
        assertNotSame(declared, type)
    }

    @Test
    fun `zipType recognises every OpenDocument mimetype, trailing newline included`() {
        odfTypes.forEach { assertEquals(it, zip(odfEntries, it)) }
        assertEquals(
            "application/vnd.oasis.opendocument.text",
            zip(odfEntries, "application/vnd.oasis.opendocument.text\n")
        )
    }

    @Test
    fun `zipType never echoes an unknown mimetype entry`() {
        assertEquals("application/zip", zip(odfEntries, "text/x-made-up"))
        assertEquals("application/zip", zip(odfEntries, "application/epub+zip".padEnd(200, 'x')))
    }

    @Test
    fun `zipType recognises DOCX, XLSX and PPTX`() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            zip(docxEntries)
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            zip(xlsxEntries)
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            zip(pptxEntries)
        )
    }

    @Test
    fun `zipType needs a part folder besides the content types entry`() {
        assertEquals("application/zip", zip(contentTypesOnly))
    }

    @Test
    fun `zipType recognises an APK by its manifest plus code or resources`() {
        assertEquals("application/vnd.android.package-archive", zip(apkEntries))
        assertEquals("application/vnd.android.package-archive", zip(apkWithoutResources))
        assertEquals("application/vnd.android.package-archive", zip(apkWithoutCode))
    }

    @Test
    fun `zipType leaves an AAR as a plain ZIP`() {
        assertEquals("application/zip", zip(aarEntries))
    }

    @Test
    fun `zipType leaves a JAR and a plain archive as ZIP`() {
        assertEquals("application/zip", zip(jarEntries))
        assertEquals("application/zip", zip(plainZipEntries))
    }

    @Test
    fun `zipType stops reading names after two thousand entries`() {
        var pulled = 0
        val names = sequence {
            repeat(2000) {
                pulled++
                yield("file$it.txt")
            }
            pulled++
            yield("[Content_Types].xml")
            yield("word/document.xml")
        }

        assertEquals("application/zip", MimeTypeSniffer.zipType(names, null))
        assertEquals(2000, pulled)
    }

    private fun sniff(bytes: ByteArray, reachedEof: Boolean = true): String? =
        MimeTypeSniffer.sniff(bytes, bytes.size, reachedEof)

    private fun zip(names: List<String>, mimetype: String? = null): String =
        MimeTypeSniffer.zipType(names.asSequence(), mimetype)

    private fun hex(text: String): ByteArray =
        text.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

    // An ISO-BMFF `ftyp` box whose size field matches its brands.
    private fun ftyp(major: String, vararg compatible: String): ByteArray {
        val size = 16 + 4 * compatible.size
        return ByteBuffer.allocate(4).putInt(size).array() + ascii("ftyp") + ascii(major) +
            hex("00 00 00 00") + compatible.fold(ByteArray(0)) { box, brand -> box + ascii(brand) }
    }

    // One Ogg page header (version 0, first page, one 30-byte segment) followed by [firstPacket].
    private fun oggPage(firstPacket: ByteArray): ByteArray =
        ascii("OggS") + hex("00 02") + ByteArray(20) + hex("01 1E") + firstPacket
}
