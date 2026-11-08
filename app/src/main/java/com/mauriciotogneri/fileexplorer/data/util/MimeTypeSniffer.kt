package com.mauriciotogneri.fileexplorer.data.util

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Recognises a file's format from its first bytes, for the files whose name gives no type.
 *
 * [MimeTypeUtil.getMimeType] answers from the name alone, so a file called `photo`, `README` or
 * `report.dat` went to other apps as "any type", and the system picker then listed every app that
 * opens some kind of file, most of which could not open this one. A few bytes at the start of the
 * file usually say what it is, so the open can name a real type instead.
 *
 * A format is recognised only when the app itself can show or route it (images, PDF, audio and
 * video, plain text, the ZIP family) and its signature is unambiguous at a fixed offset. Weaker
 * evidence is consulted later: fixed-offset signatures first, then a PDF header at a line start
 * within the first kilobyte, and plain text last, once every signature has failed.
 *
 * Every result is one of the constants in [TYPES], never text read from the file. The type is
 * reported to analytics, whose params must never carry a file's contents, which is why a ZIP's
 * `mimetype` entry is matched against an allowlist rather than passed on.
 *
 * Pure and free of I/O: the caller reads the bytes, so every rule is testable on the JVM.
 */
object MimeTypeSniffer {

    /**
     * How many leading bytes [sniff] judges. One page covers PDF's header window, the ISO-BMFF
     * brands, the Matroska DocType, the Ogg codec ID, a typical ID3 tag and a fair text sample, and
     * reading it costs no more than reading 64 bytes: opening the file is the expensive part.
     */
    const val PREFIX_BYTES = 4096

    private const val JPEG = "image/jpeg"
    private const val PNG = "image/png"
    private const val GIF = "image/gif"
    private const val WEBP = "image/webp"
    private const val AVIF = "image/avif"
    private const val HEIC = "image/heic"
    private const val HEIC_SEQUENCE = "image/heic-sequence"
    private const val HEIF = "image/heif"
    private const val HEIF_SEQUENCE = "image/heif-sequence"
    private const val WAV = "audio/x-wav"
    private const val MP4_AUDIO = "audio/mp4"
    private const val OGG_AUDIO = "audio/ogg"
    private const val FLAC = "audio/flac"
    private const val MP3 = "audio/mpeg"
    private const val AVI = "video/x-msvideo"
    private const val MP4_VIDEO = "video/mp4"
    private const val QUICKTIME = "video/quicktime"
    private const val THREE_GPP = "video/3gpp"
    private const val THREE_GPP2 = "video/3gpp2"
    private const val WEBM = "video/webm"
    private const val MATROSKA = "video/x-matroska"
    private const val OGG_VIDEO = "video/ogg"
    private const val PDF = "application/pdf"
    private const val TEXT = "text/plain"
    private const val ZIP = "application/zip"
    private const val EPUB = "application/epub+zip"
    private const val ODT = "application/vnd.oasis.opendocument.text"
    private const val ODS = "application/vnd.oasis.opendocument.spreadsheet"
    private const val ODP = "application/vnd.oasis.opendocument.presentation"
    private const val ODG = "application/vnd.oasis.opendocument.graphics"
    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    private const val APK = "application/vnd.android.package-archive"

    /** Every value [sniff] and [zipType] can return: the analytics vocabulary for sniffed types. */
    val TYPES: Set<String> = setOf(
        JPEG, PNG, GIF, WEBP, AVIF, HEIC, HEIC_SEQUENCE, HEIF, HEIF_SEQUENCE,
        WAV, MP4_AUDIO, OGG_AUDIO, FLAC, MP3,
        AVI, MP4_VIDEO, QUICKTIME, THREE_GPP, THREE_GPP2, WEBM, MATROSKA, OGG_VIDEO,
        PDF, TEXT,
        ZIP, EPUB, ODT, ODS, ODP, ODG, DOCX, XLSX, PPTX, APK
    )

    /**
     * The type [prefix] proves, or null. Only ever returns a member of [TYPES].
     *
     * @param prefix the file's first bytes; may be a larger buffer than the data it holds
     * @param length how many bytes of [prefix] are valid. Nothing past them is read
     * @param reachedEof whether the file ended within those bytes. A multi-byte character cut off
     *   by the end of the file means the file is not text; one cut off by the read cap does not
     */
    fun sniff(prefix: ByteArray, length: Int, reachedEof: Boolean): String? {
        val bytes = Prefix(prefix, length.coerceIn(0, prefix.size))
        if (bytes.size == 0) return null
        return fixedSignatureType(bytes)
            ?: (if (hasPdfHeader(bytes)) PDF else null)
            ?: (if (isText(bytes, reachedEof)) TEXT else null)
    }

    /**
     * Refines an archive [sniff] called `application/zip`, from its entry names: EPUB,
     * OpenDocument, Office and APK files are all ZIP archives, and each goes somewhere different.
     *
     * In order: a `mimetype` entry naming EPUB or one of the four OpenDocument types; then
     * `[Content_Types].xml` with an entry under `word/`, `xl/` or `ppt/` (DOCX, XLSX, PPTX); then
     * `AndroidManifest.xml` with `classes.dex` or `resources.arsc` (APK). Anything else, a JAR or
     * an AAR included, stays `application/zip`. Only the first 2,000 names are read; real files
     * are decided long before that.
     *
     * @param mimetypeEntry the text of the archive's `mimetype` entry, or null when it has none.
     *   It is only compared against the allowlist, never returned
     */
    fun zipType(entryNames: Sequence<String>, mimetypeEntry: String?): String {
        val declared = mimetypeEntry?.trim()
        // The constant from the list, not the text read from the archive (see the class KDoc).
        MIMETYPE_ENTRY_TYPES.firstOrNull { it == declared }?.let { return it }

        var contentTypes = false
        var word = false
        var excel = false
        var powerPoint = false
        var manifest = false
        var dex = false
        var resources = false
        for (name in entryNames.take(MAX_ZIP_ENTRIES)) {
            when {
                name == "[Content_Types].xml" -> contentTypes = true
                name == "AndroidManifest.xml" -> manifest = true
                name == "classes.dex" -> dex = true
                name == "resources.arsc" -> resources = true
                name.startsWith("word/") -> word = true
                name.startsWith("xl/") -> excel = true
                name.startsWith("ppt/") -> powerPoint = true
            }
        }
        return when {
            contentTypes && word -> DOCX
            contentTypes && excel -> XLSX
            contentTypes && powerPoint -> PPTX
            manifest && (dex || resources) -> APK
            else -> ZIP
        }
    }

    private fun fixedSignatureType(bytes: Prefix): String? = when {
        bytes.hasAt(0, JPEG_MAGIC) -> JPEG
        bytes.hasAt(0, PNG_MAGIC) -> PNG
        bytes.hasAt(0, GIF87A_MAGIC) || bytes.hasAt(0, GIF89A_MAGIC) -> GIF
        bytes.hasAt(0, RIFF_MAGIC) -> riffType(bytes)
        bytes.hasAt(4, FTYP_MAGIC) -> isoBmffType(bytes)
        bytes.hasAt(0, EBML_MAGIC) -> matroskaType(bytes)
        bytes.hasAt(0, OGG_MAGIC) -> oggType(bytes)
        bytes.hasAt(0, FLAC_MAGIC) -> FLAC
        bytes.hasAt(0, ID3_MAGIC) -> id3Type(bytes)
        bytes.hasAt(0, ZIP_MAGIC) -> ZIP
        else -> null
    }

    // WebP, WAV and AVI share the RIFF container; the form type at offset 8 tells them apart.
    private fun riffType(bytes: Prefix): String? = when (bytes.asciiAt(8, 4)) {
        "WEBP" -> WEBP
        "WAVE" -> WAV
        "AVI " -> AVI
        else -> null
    }

    /**
     * MP4, QuickTime, 3GP, M4A, HEIC and AVIF all open with an `ftyp` box, so the brand decides.
     * An unknown brand is no match rather than a guess at MP4: Canon's CR3 raw photos list `isom`
     * among their compatible brands, and a phone photo must never be sent to video players.
     */
    private fun isoBmffType(bytes: Prefix): String? {
        val boxSize = bytes.int32At(0)
        if (boxSize !in MIN_FTYP_BOX..MAX_FTYP_BOX || bytes.size < MIN_FTYP_BOX) return null
        val major = bytes.asciiAt(8, BRAND_LENGTH) ?: return null
        return when {
            major in AVIF_BRANDS -> AVIF
            major in HEIC_BRANDS -> HEIC
            major in HEIC_SEQUENCE_BRANDS -> HEIC_SEQUENCE
            major in HEIF_BRANDS -> heifType(major, compatibleBrands(bytes, boxSize))
            major in MP4_AUDIO_BRANDS -> MP4_AUDIO
            major == QUICKTIME_BRAND -> QUICKTIME
            major.startsWith("3g2") -> THREE_GPP2
            major.startsWith("3g") -> THREE_GPP
            major in MP4_VIDEO_BRANDS -> MP4_VIDEO
            else -> null
        }
    }

    // The generic HEIF brands say only "an image container"; the compatible brands name the codec.
    private fun heifType(major: String, compatible: List<String>): String = when {
        compatible.any { it in AVIF_BRANDS } -> AVIF
        compatible.any { it in HEIC_BRANDS } -> HEIC
        major == HEIF_SEQUENCE_BRAND -> HEIF_SEQUENCE
        else -> HEIF
    }

    // Brands follow the major brand and minor version, up to the end of the box or of the data.
    private fun compatibleBrands(bytes: Prefix, boxSize: Int): List<String> {
        val end = minOf(boxSize, bytes.size)
        return (FIRST_COMPATIBLE_BRAND..end - BRAND_LENGTH step BRAND_LENGTH)
            .mapNotNull { bytes.asciiAt(it, BRAND_LENGTH) }
    }

    // WebM is Matroska with a restricted codec set, named by the EBML header's DocType element.
    private fun matroskaType(bytes: Prefix): String {
        val window = minOf(bytes.size, EBML_DOCTYPE_WINDOW)
        for (offset in EBML_MAGIC.size until window - 1) {
            if (bytes.at(offset) == 0x42 && bytes.at(offset + 1) == 0x82) {
                return if (isWebmDocType(bytes, offset + 2)) WEBM else MATROSKA
            }
        }
        return MATROSKA
    }

    // The DocType's size is an EBML variable-length integer: its leading zero bits give its width.
    private fun isWebmDocType(bytes: Prefix, sizeOffset: Int): Boolean {
        val first = bytes.at(sizeOffset)
        if (first <= 0) return false
        val width = first.toByte().countLeadingZeroBits() + 1
        var value = (first and (0xFF ushr width)).toLong()
        for (index in 1 until width) {
            val next = bytes.at(sizeOffset + index)
            if (next < 0) return false
            value = (value shl Byte.SIZE_BITS) or next.toLong()
        }
        return value == WEBM_DOCTYPE.size.toLong() && bytes.hasAt(sizeOffset + width, WEBM_DOCTYPE)
    }

    // The first packet follows the 27-byte page header and its segment table, whose length is
    // byte 26. Only Theora and Skeleton streams carry video; the rest (Vorbis, Opus, FLAC) is
    // audio.
    private fun oggType(bytes: Prefix): String? {
        if (bytes.size < OGG_PAGE_HEADER || bytes.at(4) != 0) return null
        val packet = OGG_PAGE_HEADER + bytes.at(26)
        return if (bytes.hasAt(packet, THEORA_ID) || bytes.hasAt(packet, SKELETON_ID)) {
            OGG_VIDEO
        } else {
            OGG_AUDIO
        }
    }

    // A real ID3v2 header has version 2-4, a revision below FF and a syncsafe size (every byte
    // below 80). FLAC files sometimes carry one too, so a "fLaC" right after the tag wins over MP3.
    private fun id3Type(bytes: Prefix): String? {
        if (bytes.size < ID3_HEADER || bytes.at(3) !in 2..4 || bytes.at(4) == 0xFF) return null
        if ((6..9).any { bytes.at(it) !in 0..0x7F }) return null
        val tagSize = (6..9).fold(0) { size, index -> (size shl 7) or bytes.at(index) }
        val footer = if ((bytes.at(5) and ID3_FOOTER_FLAG) != 0) ID3_HEADER else 0
        return if (bytes.hasAt(ID3_HEADER + tagSize + footer, FLAC_MAGIC)) FLAC else MP3
    }

    // PDF readers accept the header anywhere in the first kilobyte. Requiring it at a line start
    // keeps a text file that merely mentions the marker from being taken for a PDF.
    private fun hasPdfHeader(bytes: Prefix): Boolean {
        val lastStart = minOf(bytes.size, PDF_HEADER_WINDOW) - PDF_MAGIC.size
        for (offset in 0..lastStart) {
            val lineStart = offset == 0 || bytes.at(offset - 1) == LF || bytes.at(offset - 1) == CR
            if (lineStart && bytes.hasAt(offset, PDF_MAGIC)) return true
        }
        return false
    }

    /**
     * Strict UTF-8 with no NUL and almost no control characters. Binary data that is also valid
     * UTF-8 for a whole page essentially doesn't exist, and the in-app text viewer decodes only
     * UTF-8, so legacy 8-bit text is left alone rather than shown with replacement characters.
     */
    private fun isText(bytes: Prefix, reachedEof: Boolean): Boolean {
        val start = if (bytes.hasAt(0, UTF8_BOM)) UTF8_BOM.size else 0
        val count = bytes.size - start
        if (count <= 0) return false
        for (index in start until bytes.size) {
            if (bytes.at(index) == 0) return false
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // UTF-8 never decodes to more chars than it has bytes, so the output can't overflow. With
        // endOfInput false, a character cut off at the end is left undecoded instead of rejected.
        val chars = CharBuffer.allocate(count)
        if (decoder.decode(ByteBuffer.wrap(bytes.data, start, count), chars, reachedEof).isError) {
            return false
        }
        val decoded = chars.position()
        if (decoded == 0) return false
        var controls = 0
        for (index in 0 until decoded) {
            if (isControl(chars.get(index))) controls++
        }
        return controls * 100 <= decoded * MAX_CONTROL_PERCENT
    }

    // TAB, LF, FF, CR and ESC (for ANSI-coloured logs) are ordinary in text; the other C0 and C1
    // controls and DEL are not.
    private fun isControl(char: Char): Boolean = when (char.code) {
        0x09, 0x0A, 0x0C, 0x0D, 0x1B -> false
        in 0x01..0x1F, 0x7F, in 0x80..0x9F -> true
        else -> false
    }

    /** The first [size] bytes of [data]; every read is bounds-checked against [size]. */
    private class Prefix(val data: ByteArray, val size: Int) {

        /** The unsigned byte at [index], or -1 outside the valid bytes. */
        fun at(index: Int): Int = if (index in 0 until size) data[index].toInt() and 0xFF else -1

        fun hasAt(offset: Int, signature: ByteArray): Boolean =
            offset >= 0 && offset <= size - signature.size &&
                signature.indices.all { data[offset + it] == signature[it] }

        // Latin-1 maps every byte to one char, so a brand is compared byte for byte.
        fun asciiAt(offset: Int, count: Int): String? =
            if (offset >= 0 && offset <= size - count) {
                String(data, offset, count, Charsets.ISO_8859_1)
            } else {
                null
            }

        /** The big-endian int at [offset]; negative past the data or when its top bit is set. */
        fun int32At(offset: Int): Int =
            if (offset >= 0 && offset <= size - 4) {
                (at(offset) shl 24) or (at(offset + 1) shl 16) or
                    (at(offset + 2) shl 8) or at(offset + 3)
            } else {
                -1
            }
    }

    private const val LF = 0x0A
    private const val CR = 0x0D
    private const val PDF_HEADER_WINDOW = 1024
    private const val MAX_CONTROL_PERCENT = 1
    private const val MIN_FTYP_BOX = 16
    private const val MAX_FTYP_BOX = 4096
    private const val FIRST_COMPATIBLE_BRAND = 16
    private const val BRAND_LENGTH = 4
    private const val EBML_DOCTYPE_WINDOW = 64
    private const val OGG_PAGE_HEADER = 27
    private const val ID3_HEADER = 10
    private const val ID3_FOOTER_FLAG = 0x10
    private const val MAX_ZIP_ENTRIES = 2000

    private val JPEG_MAGIC = bytes(0xFF, 0xD8, 0xFF)
    private val PNG_MAGIC = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val GIF87A_MAGIC = ascii("GIF87a")
    private val GIF89A_MAGIC = ascii("GIF89a")
    private val RIFF_MAGIC = ascii("RIFF")
    private val FTYP_MAGIC = ascii("ftyp")
    private val EBML_MAGIC = bytes(0x1A, 0x45, 0xDF, 0xA3)
    private val WEBM_DOCTYPE = ascii("webm")
    private val OGG_MAGIC = ascii("OggS")
    private val THEORA_ID = bytes(0x80) + ascii("theora")
    private val SKELETON_ID = ascii("fishead")
    private val FLAC_MAGIC = ascii("fLaC")
    private val ID3_MAGIC = ascii("ID3")
    private val PDF_MAGIC = ascii("%PDF-")
    private val ZIP_MAGIC = bytes(0x50, 0x4B, 0x03, 0x04)
    private val UTF8_BOM = bytes(0xEF, 0xBB, 0xBF)

    private val AVIF_BRANDS = setOf("avif", "avis")
    private val HEIC_BRANDS = setOf("heic", "heix", "heim", "heis")
    private val HEIC_SEQUENCE_BRANDS = setOf("hevc", "hevx", "hevm", "hevs")
    private const val HEIF_SEQUENCE_BRAND = "msf1"
    private val HEIF_BRANDS = setOf("mif1", "miaf", HEIF_SEQUENCE_BRAND)
    private val MP4_AUDIO_BRANDS = setOf("M4A ", "M4B ", "M4P ", "F4A ", "F4B ")
    private const val QUICKTIME_BRAND = "qt  "
    private val MP4_VIDEO_BRANDS = setOf(
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
        "mp41", "mp42", "mp71", "avc1", "dash", "mmp4",
        "M4V ", "M4VH", "M4VP", "f4v ", "MSNV", "XAVC"
    )

    private val MIMETYPE_ENTRY_TYPES = listOf(EPUB, ODT, ODS, ODP, ODG)

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)
}
