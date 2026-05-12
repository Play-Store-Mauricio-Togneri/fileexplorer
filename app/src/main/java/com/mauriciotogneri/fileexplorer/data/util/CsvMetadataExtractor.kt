package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.CsvMetadata
import java.io.File

object CsvMetadataExtractor {

    fun extract(file: File): CsvMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            var rowCount = 0
            var columnCount: Int? = null

            file.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        rowCount++
                        if (columnCount == null) {
                            columnCount = countColumns(line)
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

    private fun countColumns(line: String): Int {
        var count = 1
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> count++
            }
        }
        return count
    }
}
