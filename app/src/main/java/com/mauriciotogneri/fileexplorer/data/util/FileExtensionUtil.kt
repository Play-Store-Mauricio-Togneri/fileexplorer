package com.mauriciotogneri.fileexplorer.data.util

import java.io.File

object FileExtensionUtil {
    fun getExtension(path: String): String =
        File(path).extension.lowercase().ifEmpty { "unknown" }
}
