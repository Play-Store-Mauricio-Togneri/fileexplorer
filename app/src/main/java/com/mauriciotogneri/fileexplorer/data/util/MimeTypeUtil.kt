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

    fun isPdf(mimeType: String): Boolean = mimeType == "application/pdf"

    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")

    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    fun isApk(mimeType: String): Boolean = mimeType == "application/vnd.android.package-archive"

    fun isZip(mimeType: String): Boolean = mimeType in ZIP_MIME_TYPES

    fun isOfficeDocument(mimeType: String): Boolean = mimeType in OFFICE_MIME_TYPES

    fun isEpub(mimeType: String): Boolean = mimeType == "application/epub+zip"

    private val ZIP_MIME_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed"
    )

    private val OFFICE_MIME_TYPES = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
}
