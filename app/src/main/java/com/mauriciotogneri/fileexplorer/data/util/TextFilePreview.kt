package com.mauriciotogneri.fileexplorer.data.util

import java.io.File

/**
 * Result of reading a bounded preview of a text file.
 *
 * @param lines the decoded text split into lines (LF or CRLF tolerated, CR stripped); lines
 *   longer than [TextFilePreview.MAX_LINE_LENGTH] are hard-wrapped into consecutive chunks
 * @param truncated true when only the beginning of the file is present, either because it was
 *   larger than the requested byte cap or because it held more lines than the line cap
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

    // Ceiling on how many lines a preview holds. Every line is a separate String kept alive for as
    // long as the viewer is open, so a degenerate file (a megabyte of newlines is a million of
    // them) costs many times its own size in object overhead alone. Real text hits the byte cap
    // first: 50,000 lines is a megabyte at 20 bytes per line.
    const val MAX_LINES = 50_000

    // Starting buffer for a file whose length the filesystem doesn't report (0 bytes, as /proc
    // and /sys entries do). Grows toward the byte cap as the read fills it.
    private const val INITIAL_BUFFER_SIZE = 8 * 1024

    private class Lines(val values: List<String>, val capped: Boolean)

    fun read(
        file: File,
        maxBytes: Int,
        maxLineLength: Int = MAX_LINE_LENGTH,
        maxLines: Int = MAX_LINES
    ): TextPreview {
        val cap = maxBytes.coerceAtLeast(1)
        val lineCap = maxLineLength.coerceAtLeast(1)
        val lineCountCap = maxLines.coerceAtLeast(1)

        // Size the buffer to the file instead of always allocating the cap: most text files are a
        // tiny fraction of it, and the allocation happens on every open.
        var buffer = ByteArray(initialBufferSize(file, cap))
        var total = 0
        var truncated = false

        file.inputStream().use { input ->
            while (total < cap) {
                if (total == buffer.size) {
                    // The file is longer than it reported (or grew mid-read): grow toward the cap.
                    buffer = buffer.copyOf((buffer.size.toLong() * 2).coerceAtMost(cap.toLong()).toInt())
                }
                val read = input.read(buffer, total, buffer.size - total)
                if (read == -1) break
                total += read
            }
            // We filled the cap exactly; if another byte exists, the file was larger.
            if (total == cap) {
                truncated = input.read() != -1
            }
        }

        val text = String(buffer, 0, total, Charsets.UTF_8)
        val lines = splitLines(text, lineCap, lineCountCap)
        return TextPreview(lines = lines.values, truncated = truncated || lines.capped)
    }

    private fun initialBufferSize(file: File, cap: Int): Int {
        val declaredSize = file.length().coerceIn(0L, cap.toLong()).toInt()
        // One byte of slack past the reported length, so reading a whole file hits EOF instead of
        // filling the buffer exactly and paying for a growth it doesn't need.
        return if (declaredSize == 0) {
            INITIAL_BUFFER_SIZE.coerceAtMost(cap)
        } else {
            (declaredSize + 1).coerceAtMost(cap)
        }
    }

    /**
     * Splits [text] on LF (a trailing CR of a CRLF pair is dropped), hard-wrapping any line longer
     * than [maxLength] and stopping once [maxLines] lines have been collected.
     *
     * Written as a single pass that appends straight into the result list: splitting and then
     * flat-mapping the wrap would hold two more copies of every line at once, which for a file of
     * very short lines is the largest allocation the viewer makes.
     */
    private fun splitLines(text: String, maxLength: Int, maxLines: Int): Lines {
        if (text.isEmpty()) return Lines(emptyList(), capped = false)

        val lines = ArrayList<String>()
        var lineStart = 0

        while (true) {
            val newline = text.indexOf('\n', lineStart)
            val rawEnd = if (newline == -1) text.length else newline
            // Strip the CR of a CRLF line ending.
            val lineEnd = if (rawEnd > lineStart && text[rawEnd - 1] == '\r') rawEnd - 1 else rawEnd

            // Stopped mid-line, or finished it with more lines still to come: either way the
            // preview is incomplete and the caller must flag it.
            if (!appendWrapped(lines, text, lineStart, lineEnd, maxLength, maxLines)) {
                return Lines(lines, capped = true)
            }
            if (newline == -1) return Lines(lines, capped = false)
            if (lines.size >= maxLines) return Lines(lines, capped = true)

            lineStart = newline + 1
        }
    }

    /**
     * Appends `text[start, end)` to [lines] as chunks of at most [maxLength] characters (plus one
     * when a surrogate pair would otherwise be broken across the boundary). An empty range still
     * contributes one empty line, so blank lines keep their vertical space.
     *
     * Returns false when [lines] reached [maxLines] before the whole range was appended.
     */
    private fun appendWrapped(
        lines: MutableList<String>,
        text: String,
        start: Int,
        end: Int,
        maxLength: Int,
        maxLines: Int
    ): Boolean {
        var chunkStart = start
        while (lines.size < maxLines) {
            var chunkEnd = (chunkStart + maxLength).coerceAtMost(end)
            // Keep a surrogate pair intact if the boundary lands between its two halves.
            if (chunkEnd < end && text[chunkEnd - 1].isHighSurrogate() && text[chunkEnd].isLowSurrogate()) {
                chunkEnd++
            }
            lines.add(text.substring(chunkStart, chunkEnd))
            chunkStart = chunkEnd
            if (chunkStart >= end) return true
        }
        return false
    }
}
