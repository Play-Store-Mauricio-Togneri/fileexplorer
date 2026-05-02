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
}
