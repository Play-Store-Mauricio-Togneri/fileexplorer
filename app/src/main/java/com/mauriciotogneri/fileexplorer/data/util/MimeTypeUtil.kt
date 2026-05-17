package com.mauriciotogneri.fileexplorer.data.util

import android.webkit.MimeTypeMap
import java.io.File
import java.net.URLConnection

object MimeTypeUtil {

    fun getMimeType(file: File): String {
        return URLConnection.guessContentTypeFromName(file.absolutePath)
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"
    }

    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    fun hasNativeThumbnailSupport(mimeType: String, fileName: String): Boolean {
        if (!isImage(mimeType)) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext !in UNSUPPORTED_IMAGE_EXTENSIONS && mimeType !in UNSUPPORTED_IMAGE_MIME_TYPES
    }

    private val UNSUPPORTED_IMAGE_EXTENSIONS = setOf(
        "tiff", "tif",
        "heic", "heif",
        "avif",
        "svg", "svgz",
        "cr2", "cr3", "nef", "arw", "dng", "raf", "orf", "rw2", "pef", "srw"
    )

    private val UNSUPPORTED_IMAGE_MIME_TYPES = setOf(
        "image/tiff",
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence",
        "image/avif",
        "image/svg+xml",
        "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef", "image/x-sony-arw",
        "image/x-adobe-dng", "image/x-fuji-raf", "image/x-olympus-orf", "image/x-panasonic-rw2",
        "image/x-pentax-pef", "image/x-samsung-srw"
    )

    fun isPdf(mimeType: String): Boolean = mimeType == "application/pdf"

    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")

    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    fun isApk(mimeType: String): Boolean = mimeType == "application/vnd.android.package-archive"

    fun isZip(mimeType: String): Boolean = mimeType in ZIP_MIME_TYPES

    fun isArchive(mimeType: String): Boolean = mimeType in ARCHIVE_MIME_TYPES

    fun isOfficeDocument(mimeType: String): Boolean = mimeType in OFFICE_MIME_TYPES

    fun isEpub(mimeType: String): Boolean = mimeType == "application/epub+zip"

    fun isFont(mimeType: String): Boolean = mimeType in FONT_MIME_TYPES

    fun isFontByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in FONT_EXTENSIONS
    }

    fun isSvg(mimeType: String): Boolean = mimeType == "image/svg+xml"

    fun isSvgByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "svg" || ext == "svgz"
    }

    fun isSqlite(mimeType: String): Boolean = mimeType in SQLITE_MIME_TYPES

    fun isVCard(mimeType: String): Boolean = mimeType == "text/vcard" || mimeType == "text/x-vcard"

    fun isICalendar(mimeType: String): Boolean = mimeType == "text/calendar"

    fun isCsv(mimeType: String): Boolean = mimeType == "text/csv" || mimeType == "text/comma-separated-values"

    fun isSqliteByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SQLITE_EXTENSIONS
    }

    fun isVCardByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "vcf" || ext == "vcard"
    }

    fun isICalendarByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "ics" || ext == "ical" || ext == "ifb"
    }

    fun isCsvByExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "csv"
    }

    private val SQLITE_MIME_TYPES = setOf(
        "application/vnd.sqlite3",
        "application/x-sqlite3",
        "application/x-sqlite",
        "application/sqlite"
    )

    private val SQLITE_EXTENSIONS = setOf(
        "db", "sqlite", "sqlite3", "db3"
    )

    private val ZIP_MIME_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip"
    )

    private val ARCHIVE_MIME_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-zip",
        "application/rar",
        "application/vnd.rar",
        "application/x-rar-compressed",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip",
        "application/x-gzip",
        "application/x-bzip2",
        "application/x-xz",
        "application/x-lzip",
        "application/x-lzma",
        "application/x-compress",
        "application/zstd",
        "application/x-zstd",
        "application/x-lz4",
        "application/vnd.ms-cab-compressed",
        "application/x-iso9660-image",
        "application/x-apple-diskimage",
        "application/x-cpio"
    )

    private val FONT_MIME_TYPES = setOf(
        "font/ttf",
        "font/otf",
        "font/woff",
        "font/woff2",
        "font/sfnt",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/font-woff",
        "application/font-woff2",
        "application/vnd.ms-fontobject",
        "application/vnd.ms-opentype",
        "application/font-sfnt"
    )

    private val FONT_EXTENSIONS = setOf(
        "ttf", "otf", "woff", "woff2", "eot", "sfnt"
    )

    private val OFFICE_MIME_TYPES = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation"
    )
}
