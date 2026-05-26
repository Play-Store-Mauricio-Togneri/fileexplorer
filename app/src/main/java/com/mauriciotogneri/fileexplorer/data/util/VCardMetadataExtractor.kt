package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.VCardMetadata
import java.io.File

object VCardMetadataExtractor {

    fun extract(file: File): VCardMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            var contactCount = 0
            var hasPhoneNumbers = false
            var hasEmails = false
            var hasPhotos = false

            file.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    runCatching {
                        val upperLine = line.uppercase()
                        when {
                            upperLine.startsWith("BEGIN:VCARD") -> contactCount++
                            upperLine.startsWith("TEL") -> hasPhoneNumbers = true
                            upperLine.startsWith("EMAIL") -> hasEmails = true
                            upperLine.startsWith("PHOTO") -> hasPhotos = true
                        }
                    }
                }
            }

            if (contactCount == 0) return null

            VCardMetadata(
                contactCount = contactCount,
                hasPhoneNumbers = hasPhoneNumbers.takeIf { it },
                hasEmails = hasEmails.takeIf { it },
                hasPhotos = hasPhotos.takeIf { it }
            )
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_vcard_metadata", "vcard")
            null
        }
    }
}
