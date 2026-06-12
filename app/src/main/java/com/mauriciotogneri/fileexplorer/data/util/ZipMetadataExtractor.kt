package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import java.io.File
import java.util.zip.ZipFile

object ZipMetadataExtractor {

    fun extract(file: File): ZipMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            ZipFile(file).use { zip ->
                var entryCount = 0
                var compressedSize = 0L
                var uncompressedSize = 0L

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entry.compressedSize > 0) {
                        compressedSize += entry.compressedSize
                    }
                    if (entry.size > 0) {
                        uncompressedSize += entry.size
                    }
                }

                ZipMetadata(
                    entryCount = entryCount.takeIf { it > 0 },
                    compressedSize = compressedSize.takeIf { it > 0 },
                    uncompressedSize = uncompressedSize.takeIf { it > 0 }
                )
            }
        } catch (e: Exception) {
            // A corrupted or non-ZIP file makes ZipFile throw ZipException. These
            // are expected, unactionable conditions and not worth reporting.
            if (!isUnreadableZip(e)) {
                ErrorReporter.warning(e, "extract_zip_metadata", "zip")
            }
            null
        }
    }
}
