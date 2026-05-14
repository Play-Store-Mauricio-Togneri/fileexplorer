package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.OfficeMetadata
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

object OfficeMetadataExtractor {

    fun extract(file: File): OfficeMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            ZipFile(file).use { zip ->
                val coreEntry = zip.getEntry("docProps/core.xml") ?: return null
                val xmlContent = zip.getInputStream(coreEntry).bufferedReader().use { it.readText() }
                parseCorePropXml(xmlContent)
            }
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_office_metadata", "office")
            null
        }
    }

    private fun parseCorePropXml(xml: String): OfficeMetadata? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var title: String? = null
            var creator: String? = null
            var subject: String? = null
            var keywords: String? = null
            var createdDate: String? = null
            var modifiedDate: String? = null

            var eventType = parser.eventType
            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                runCatching {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text?.trim()?.takeIf { it.isNotBlank() }
                            when (currentTag) {
                                "title" -> title = text
                                "creator" -> creator = text
                                "subject" -> subject = text
                                "keywords" -> keywords = text
                                "created" -> createdDate = text
                                "modified" -> modifiedDate = text
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            currentTag = null
                        }
                    }
                }
                eventType = runCatching { parser.next() }.getOrElse { XmlPullParser.END_DOCUMENT }
            }

            OfficeMetadata(
                title = title,
                creator = creator,
                subject = subject,
                keywords = keywords,
                createdDate = createdDate,
                modifiedDate = modifiedDate
            )
        } catch (e: Exception) {
            ErrorReporter.warning(e, "parse_office_core_xml", "office")
            null
        }
    }
}
