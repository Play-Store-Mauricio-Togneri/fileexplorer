package com.mauriciotogneri.fileexplorer.data.util

import java.io.File

/**
 * Result of reading a bounded preview of a text file.
 *
 * @param lines the decoded text split into lines (LF or CRLF tolerated, CR stripped)
 * @param truncated true when the file was larger than the requested cap and only the
 *   beginning was read
 */
data class TextPreview(
    val lines: List<String>,
    val truncated: Boolean
)

/**
 * Reads the leading portion of a file as UTF-8 text without loading the whole file into memory.
 *
 * Bytes are decoded as UTF-8 with malformed/unmappable input replaced by U+FFFD, so binary or
 * mis-detected files never throw on decoding (callers should still guard the I/O itself).
 */
object TextFilePreview {

    fun read(file: File, maxBytes: Int): TextPreview {
        val cap = maxBytes.coerceAtLeast(1)
        val buffer = ByteArray(cap)
        var total = 0
        var truncated = false

        file.inputStream().use { input ->
            while (total < cap) {
                val read = input.read(buffer, total, cap - total)
                if (read == -1) break
                total += read
            }
            // We filled the cap exactly; if another byte exists, the file was larger.
            if (total == cap) {
                truncated = input.read() != -1
            }
        }

        val text = String(buffer, 0, total, Charsets.UTF_8)
        val lines = if (text.isEmpty()) {
            emptyList()
        } else {
            text.split('\n').map { it.removeSuffix("\r") }
        }
        return TextPreview(lines = lines, truncated = truncated)
    }
}
