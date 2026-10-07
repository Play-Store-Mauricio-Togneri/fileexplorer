package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every model that carries a file type answers `MIME type || extension` for fonts, SVG, SQLite,
 * vCard, iCalendar and CSV. The platform reports many of these as a generic type, so the extension
 * half is what gives them their icon and metadata. Each model has its own copy of the expression,
 * so each is checked here with a MIME type that matches nothing.
 */
class FileTypeExtensionFallbackTest {

    private val cases: List<Pair<String, (FileTypeInfo) -> Boolean>> = listOf(
        "font.ttf" to { it.isFont },
        "logo.svg" to { it.isSvg },
        "data.db" to { it.isSqlite },
        "contact.vcf" to { it.isVCard },
        "meeting.ics" to { it.isICalendar },
        "table.csv" to { it.isCsv }
    )

    @Test
    fun `FileItem falls back to the extension when the MIME type is generic`() {
        assertFallback { name -> fileItem(name, GENERIC_MIME) }
    }

    @Test
    fun `Favorite falls back to the extension when the MIME type is generic`() {
        assertFallback { name -> favorite(name, GENERIC_MIME) }
    }

    @Test
    fun `RecentFile falls back to the extension when the MIME type is generic`() {
        assertFallback { name -> recentFile(name, GENERIC_MIME) }
    }

    @Test
    fun `a generic MIME type with an unrelated extension is none of these kinds`() {
        listOf<(String) -> FileTypeInfo>(
            { fileItem(it, GENERIC_MIME) },
            { favorite(it, GENERIC_MIME) },
            { recentFile(it, GENERIC_MIME) }
        ).forEach { build ->
            val item = build("blob.bin")
            cases.forEach { (name, predicate) ->
                assertEquals("${item::class.simpleName} blob.bin as $name's kind", false, predicate(item))
            }
        }
    }

    private fun assertFallback(build: (String) -> FileTypeInfo) {
        cases.forEach { (name, predicate) ->
            val item = build(name)
            assertEquals("${item::class.simpleName} $name", true, predicate(item))
        }
    }

    private fun fileItem(name: String, mimeType: String) = FileItem(
        path = "/storage/emulated/0/$name",
        name = name,
        isDirectory = false,
        size = 1,
        lastModified = 0,
        createdTime = 0,
        mimeType = mimeType,
        childCount = null
    )

    private fun favorite(name: String, mimeType: String) = Favorite(
        path = "/storage/emulated/0/$name",
        name = name,
        isDirectory = false,
        mimeType = mimeType,
        favoritedTimestamp = 0
    )

    private fun recentFile(name: String, mimeType: String) = RecentFile(
        path = "/storage/emulated/0/$name",
        name = name,
        mimeType = mimeType,
        lastOpenedTimestamp = 0
    )

    private companion object {
        const val GENERIC_MIME = "*/*"
    }
}
