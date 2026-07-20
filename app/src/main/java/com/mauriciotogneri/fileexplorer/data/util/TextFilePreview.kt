package com.mauriciotogneri.fileexplorer.data.util

import java.io.File

/**
 * Result of reading a bounded preview of a text file.
 *
 * @param lines the decoded text split into lines (LF or CRLF tolerated, CR stripped); lines
 *   longer than [TextFilePreview.MAX_LINE_LENGTH] are hard-wrapped into consecutive chunks
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
 *
 * Individual lines are hard-wrapped at [MAX_LINE_LENGTH] characters: a single pathologically long
 * line (e.g. minified JSON, a base64 blob, or a binary mis-detected as text) must never be handed
 * whole to the text layout engine, which can crash natively while line-breaking an enormous run.
 */
object TextFilePreview {

    // A rendered line longer than this is split into consecutive chunks. 5,000 characters is
    // already far past readable width yet keeps every laid-out string small.
    const val MAX_LINE_LENGTH = 5000

    fun read(file: File, maxBytes: Int, maxLineLength: Int = MAX_LINE_LENGTH): TextPreview {
        val cap = maxBytes.coerceAtLeast(1)
        val lineCap = maxLineLength.coerceAtLeast(1)
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
            text.split('\n').flatMap { rawLine -> wrapLine(rawLine.removeSuffix("\r"), lineCap) }
        }
        return TextPreview(lines = lines, truncated = truncated)
    }

    /**
     * Splits [line] into consecutive chunks of at most [maxLength] characters (plus one when a
     * surrogate pair would otherwise be broken across the boundary). Empty and short lines are
     * returned unchanged.
     */
    private fun wrapLine(line: String, maxLength: Int): List<String> {
        if (line.length <= maxLength) return listOf(line)

        val chunks = ArrayList<String>((line.length / maxLength) + 1)
        var start = 0
        while (start < line.length) {
            var end = minOf(start + maxLength, line.length)
            // Keep a surrogate pair intact if the boundary lands between its two halves.
            if (end < line.length && line[end - 1].isHighSurrogate() && line[end].isLowSurrogate()) {
                end++
            }
            chunks.add(line.substring(start, end))
            start = end
        }
        return chunks
    }
}
