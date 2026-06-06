package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.EpubMetadata
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

object EpubMetadataExtractor {

    fun extract(file: File): EpubMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                parseOpfMetadata(opfContent)
            }
        } catch (e: Exception) {
            // A corrupted or non-EPUB file makes ZipFile throw ZipException. These
            // are expected, unactionable conditions and not worth reporting.
            if (!isUnreadableZip(e)) {
                ErrorReporter.warning(e, "extract_epub_metadata", "epub")
            }
            null
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        return try {
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(containerXml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val fullPath = runCatching {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                        parser.getAttributeValue(null, "full-path")
                    } else null
                }.getOrNull()
                if (fullPath != null) return fullPath
                eventType = runCatching { parser.next() }.getOrElse { XmlPullParser.END_DOCUMENT }
            }
            null
        } catch (e: Exception) {
            // A corrupt EPUB entry can throw ZipException during inflation; expected, not worth reporting.
            if (!isUnreadableZip(e)) {
                ErrorReporter.warning(e, "find_epub_opf_path", "epub")
            }
            null
        }
    }

    private fun parseOpfMetadata(xml: String): EpubMetadata? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var title: String? = null
            var creator: String? = null
            var publisher: String? = null
            var language: String? = null
            var date: String? = null
            var description: String? = null

            var eventType = parser.eventType
            var currentTag: String? = null
            var inMetadata = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                runCatching {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "metadata") {
                                inMetadata = true
                            } else if (inMetadata) {
                                currentTag = parser.name
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inMetadata) {
                                val text = parser.text?.trim()?.takeIf { it.isNotBlank() }
                                when (currentTag) {
                                    "title" -> if (title == null) title = text
                                    "creator" -> if (creator == null) creator = text
                                    "publisher" -> if (publisher == null) publisher = text
                                    "language" -> if (language == null) language = text
                                    "date" -> if (date == null) date = text
                                    "description" -> if (description == null) description = text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "metadata") {
                                inMetadata = false
                            }
                            currentTag = null
                        }
                    }
                }
                eventType = runCatching { parser.next() }.getOrElse { XmlPullParser.END_DOCUMENT }
            }

            EpubMetadata(
                title = title,
                creator = creator,
                publisher = publisher,
                language = language,
                date = date,
                description = description
            )
        } catch (e: Exception) {
            ErrorReporter.warning(e, "parse_epub_opf_metadata", "epub")
            null
        }
    }
}
