package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The by-extension predicates are the fallback for files the platform reports with a generic MIME
 * type (`application/octet-stream`, the wildcard type): a `.db` file it does not recognise is
 * identified as SQLite — and gets its icon and metadata — only through
 * [MimeTypeUtil.isSqliteByExtension]. The extensions are spelled out here rather than read from
 * production's sets, so dropping one fails.
 */
class MimeTypeUtilByExtensionTest {

    private val predicates: Map<String, Pair<(String) -> Boolean, List<String>>> = mapOf(
        "font" to (MimeTypeUtil::isFontByExtension to listOf("ttf", "otf", "woff", "woff2", "eot", "sfnt")),
        "svg" to (MimeTypeUtil::isSvgByExtension to listOf("svg", "svgz")),
        "sqlite" to (MimeTypeUtil::isSqliteByExtension to listOf("db", "sqlite", "sqlite3", "db3")),
        "vcard" to (MimeTypeUtil::isVCardByExtension to listOf("vcf", "vcard")),
        "icalendar" to (MimeTypeUtil::isICalendarByExtension to listOf("ics", "ical", "ifb")),
        "csv" to (MimeTypeUtil::isCsvByExtension to listOf("csv"))
    )

    @Test
    fun `each predicate accepts its own extensions in any case`() {
        predicates.forEach { (kind, entry) ->
            val (predicate, extensions) = entry
            extensions.forEach { ext ->
                listOf("file.$ext", "FILE.${ext.uppercase()}", "File.${ext.replaceFirstChar(Char::uppercase)}")
                    .forEach { name -> assertEquals("$kind: $name", true, predicate(name)) }
            }
        }
    }

    @Test
    fun `each predicate rejects every other kind's extensions`() {
        predicates.forEach { (kind, entry) ->
            val predicate = entry.first
            predicates.filterKeys { it != kind }.values.flatMap { it.second }.forEach { ext ->
                assertEquals("$kind: file.$ext", false, predicate("file.$ext"))
            }
        }
    }

    @Test
    fun `a name without an extension matches nothing, even when it spells one`() {
        predicates.forEach { (kind, entry) ->
            val (predicate, extensions) = entry
            extensions.forEach { ext ->
                assertEquals("$kind: $ext", false, predicate(ext))
            }
            assertEquals("$kind: README", false, predicate("README"))
            assertEquals("$kind: empty", false, predicate(""))
        }
    }

    @Test
    fun `only the last extension counts`() {
        predicates.forEach { (kind, entry) ->
            val (predicate, extensions) = entry
            val ext = extensions.first()
            assertEquals("$kind: backup.txt.$ext", true, predicate("backup.txt.$ext"))
            assertEquals("$kind: backup.$ext.txt", false, predicate("backup.$ext.txt"))
        }
    }
}
