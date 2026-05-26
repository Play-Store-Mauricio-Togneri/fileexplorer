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

                val entries = runCatching { zip.entries() }.getOrNull() ?: return null
                while (runCatching { entries.hasMoreElements() }.getOrNull() == true) {
                    val entry = runCatching { entries.nextElement() }.getOrNull() ?: break
                    runCatching {
                        entryCount++
                        if (entry.compressedSize > 0) {
                            compressedSize += entry.compressedSize
                        }
                        if (entry.size > 0) {
                            uncompressedSize += entry.size
                        }
                    }
                }

                ZipMetadata(
                    entryCount = entryCount.takeIf { it > 0 },
                    compressedSize = compressedSize.takeIf { it > 0 },
                    uncompressedSize = uncompressedSize.takeIf { it > 0 }
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
