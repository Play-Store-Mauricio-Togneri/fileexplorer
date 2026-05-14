package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.ICalendarMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ICalendarMetadataExtractor {

    private val dateFormatPatterns = listOf(
        "yyyyMMdd'T'HHmmss'Z'",
        "yyyyMMdd'T'HHmmss",
        "yyyyMMdd"
    )

    fun extract(file: File): ICalendarMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            var eventCount = 0
            var todoCount = 0
            var earliestDate: Date? = null
            var latestDate: Date? = null

            file.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    runCatching {
                        val upperLine = line.uppercase()
                        when {
                            upperLine.startsWith("BEGIN:VEVENT") -> eventCount++
                            upperLine.startsWith("BEGIN:VTODO") -> todoCount++
                            upperLine.startsWith("DTSTART") || upperLine.startsWith("DTEND") -> {
                                val dateStr = extractDateValue(line)
                                val date = parseDate(dateStr)
                                if (date != null) {
                                    if (earliestDate == null || date.before(earliestDate)) {
                                        earliestDate = date
                                    }
                                    if (latestDate == null || date.after(latestDate)) {
                                        latestDate = date
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (eventCount == 0 && todoCount == 0) return null

            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            ICalendarMetadata(
                eventCount = eventCount.takeIf { it > 0 },
                todoCount = todoCount.takeIf { it > 0 },
                earliestDate = runCatching { earliestDate?.let { outputFormat.format(it) } }.getOrNull(),
                latestDate = runCatching { latestDate?.let { outputFormat.format(it) } }.getOrNull()
            )
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_icalendar_metadata", "ics")
            null
        }
    }

    private fun extractDateValue(line: String): String {
        val colonIndex = line.indexOf(':')
        return if (colonIndex >= 0) {
            line.substring(colonIndex + 1).trim()
        } else {
            line.trim()
        }
    }

    private fun parseDate(dateStr: String): Date? {
        for (pattern in dateFormatPatterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(dateStr)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }
}
