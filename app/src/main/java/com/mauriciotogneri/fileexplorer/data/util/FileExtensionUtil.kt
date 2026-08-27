package com.mauriciotogneri.fileexplorer.data.util

import java.io.File

/**
 * The `extension` analytics parameter, which every call site derives from a path the user chose.
 *
 * `CLAUDE.md` allows an event to describe a file and never to identify it, and names the extension
 * as one of the describing values. That holds only while the value is drawn from a fixed
 * vocabulary; [File.extension] is `substringAfterLast('.', "")`, which is not one. A dotfile has no
 * dot but its first, so `.private-journal` yields `private-journal` — the whole name. A name with a
 * dot in its body yields the tail, so `Q3.Acme Confidential` yields `acme confidential`. Both ship
 * the user's own words, and this is the one parameter here that reaches Firebase Analytics rather
 * than Crashlytics, so it lands in a dataset kept for product measurement.
 *
 * So the value is an allowlist lookup rather than a substring: a name resolves to an extension only
 * if that extension is one the app already knows, and every other shape collapses to [UNKNOWN].
 * Nothing a user typed can reach an event, because nothing that is not already in this file can.
 *
 * The trade is that a format the allowlist has not heard of reports as [UNKNOWN] until
 * [KNOWN_EXTENSIONS] grows, which is deliberate: an under-reported format costs a row in a
 * dashboard, and a leaked one cannot be taken back out of the dataset.
 *
 * Most of the vocabulary is [MimeTypeUtil]'s, which already curates the extensions the app
 * recognises for text, images, fonts and SQLite. The sets below cover what that leaves: the
 * categories it routes by MIME type alone and so keeps no extension list for, plus the image
 * formats its viewer-oriented lists have no reason to name.
 */
object FileExtensionUtil {
    private const val UNKNOWN = "unknown"

    /**
     * Reports the file's extension when the app recognises it, and [UNKNOWN] otherwise — for a
     * dotfile, for a name with no extension, and for an extension not in [KNOWN_EXTENSIONS] alike.
     */
    fun getExtension(path: String): String {
        val name = File(path).name
        val separator = name.lastIndexOf('.')
        // `> 0` rather than `>= 0`: a leading dot opens a dotfile's name, it does not close a stem.
        val extension = if (separator > 0) name.substring(separator + 1).lowercase() else ""

        return if (extension in KNOWN_EXTENSIONS) extension else UNKNOWN
    }

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "mp2", "wav", "wave", "ogg", "oga", "opus", "flac", "aac", "m4a",
        "m4b", "m4p", "wma", "aiff", "aif", "aifc", "alac", "amr", "ape", "au",
        "mid", "midi", "mka", "ra", "dsf", "dff", "wv", "ac3", "dts", "caf",
        "3ga", "gsm", "spx", "tta", "voc"
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "qt", "wmv", "asf", "flv",
        "f4v", "mpg", "mpeg", "mpe", "m1v", "m2v", "3gp", "3g2", "ts", "m2ts",
        "mts", "ogv", "vob", "rm", "rmvb", "divx", "mxf", "y4m"
    )

    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "zipx", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "tbz2",
        "xz", "txz", "lz", "lzma", "lzo", "lz4", "zst", "zstd", "z", "taz",
        "cab", "arj", "lzh", "lha", "sit", "sitx", "ace", "cpio", "ar", "shar",
        "iso", "dmg", "img", "vhd", "vhdx", "vmdk"
    )

    private val PACKAGE_EXTENSIONS = setOf(
        "apk", "apks", "xapk", "aab", "apkm", "jar", "war", "ear", "aar",
        "deb", "rpm", "msi", "msix", "appx", "pkg", "snap", "flatpak", "appimage",
        "exe", "dll", "so", "dylib", "com", "bin", "elf", "o", "a", "lib",
        "crx", "xpi", "vsix", "nupkg", "whl", "egg", "gem", "dex", "class"
    )

    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "epub", "mobi", "azw", "azw3", "azw4", "kfx", "prc", "fb2",
        "lit", "pdb", "djvu", "djv", "cbz", "cbr", "cb7", "cbt", "cba",
        "chm", "ps", "eps", "xps", "oxps", "hlp"
    )

    private val OFFICE_EXTENSIONS = setOf(
        "doc", "docx", "docm", "dotx", "dotm", "rtf", "odt", "ott", "fodt",
        "xls", "xlsx", "xlsm", "xlsb", "xlt", "xltx", "ods", "ots", "fods",
        "ppt", "pptx", "pptm", "pps", "ppsx", "ppsm", "pot", "potx", "odp",
        "otp", "fodp", "odg", "otg", "odf", "odc", "odb", "mdb", "accdb",
        "vsd", "vsdx", "vst", "vstx", "pub", "one", "onetoc2", "msg", "eml",
        "wpd", "wps", "wk1", "wk4", "numbers", "pages", "key", "gsheet", "gdoc"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "ico", "cur", "apng", "mng", "psd", "psb", "xcf", "ai", "indd", "cdr",
        "jp2", "j2k", "jpf", "jpx", "jpm", "jxl", "jxr", "wdp", "hdp",
        "pbm", "pgm", "ppm", "pnm", "pam", "pcx", "tga", "icb", "dds", "ktx",
        "exr", "hdr", "pfm", "wmf", "emf", "xbm", "xpm", "ras", "sgi", "rgb",
        "eip", "3fa", "iiq", "erf", "mos", "mrw", "x3f", "kdc", "dcr", "srf",
        "sr2", "bay", "cap", "crw", "raw", "rwl", "nrw", "gpr"
    )

    private val CONTACT_AND_CALENDAR_EXTENSIONS = setOf(
        "vcf", "vcard", "ics", "ical", "ifb", "icalendar", "vcs"
    )

    private val CREDENTIAL_EXTENSIONS = setOf(
        "pem", "crt", "cer", "der", "p7b", "p7c", "p12", "pfx", "csr",
        "keystore", "jks", "bks", "asc", "gpg", "pgp", "sig", "kdbx"
    )

    private val TRANSIENT_EXTENSIONS = setOf(
        "bak", "backup", "old", "tmp", "temp", "part", "partial", "crdownload",
        "download", "opdownload", "lock", "pid", "swp", "swo", "cache", "dat",
        "idx", "meta", "checksum", "md5", "sha1", "sha256", "sfv", "torrent",
        "url", "lnk", "webloc", "shortcut"
    )

    /**
     * Every extension an event may name. A lookup miss is not a gap to be papered over at the call
     * site — it is the guarantee that the parameter carries this file's vocabulary and no other.
     */
    private val KNOWN_EXTENSIONS: Set<String> =
        MimeTypeUtil.TEXT_EXTENSIONS +
            MimeTypeUtil.VIEWABLE_IMAGE_EXTENSIONS +
            MimeTypeUtil.UNSUPPORTED_IMAGE_EXTENSIONS +
            MimeTypeUtil.HEIF_IMAGE_EXTENSIONS +
            MimeTypeUtil.FONT_EXTENSIONS +
            MimeTypeUtil.SQLITE_EXTENSIONS +
            AUDIO_EXTENSIONS +
            VIDEO_EXTENSIONS +
            ARCHIVE_EXTENSIONS +
            PACKAGE_EXTENSIONS +
            DOCUMENT_EXTENSIONS +
            OFFICE_EXTENSIONS +
            IMAGE_EXTENSIONS +
            CONTACT_AND_CALENDAR_EXTENSIONS +
            CREDENTIAL_EXTENSIONS +
            TRANSIENT_EXTENSIONS
}
