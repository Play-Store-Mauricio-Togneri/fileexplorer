package com.mauriciotogneri.fileexplorer.data.util

import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

object FileSizeFormatter {

    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
            .coerceAtMost(units.lastIndex)
        val size = bytes / 1024.0.pow(digitGroups.toDouble())

        return DecimalFormat("#,##0.#").format(size) + " " + units[digitGroups]
    }
}
