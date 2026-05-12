package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.CsvMetadata
import java.io.File

object CsvMetadataExtractor {

    private val SEPARATORS = listOf(',', ';', '\t', '|')

    fun extract(file: File): CsvMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            var rowCount = 0
            var columnCount: Int? = null
            var separator: Char? = null

            file.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        rowCount++
                        if (columnCount == null) {
                            separator = detectSeparator(line)
                            columnCount = countColumns(line, separator ?: ',')
                        }
                    }
                }
            }

            if (rowCount == 0) return null

            CsvMetadata(
                rowCount = rowCount,
                columnCount = columnCount
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun detectSeparator(line: String): Char {
        val counts = SEPARATORS.associateWith { sep -> countOccurrences(line, sep) }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun countOccurrences(line: String, separator: Char): Int {
        var count = 0
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == separator && !inQuotes -> count++
            }
        }
        return count
    }

    private fun countColumns(line: String, separator: Char): Int {
        return countOccurrences(line, separator) + 1
    }
}
