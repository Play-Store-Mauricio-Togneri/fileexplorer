package com.mauriciotogneri.fileexplorer.data.util

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File
import java.util.zip.ZipFile

class EpubThumbnailFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            extractCoverImage()
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_epub_thumbnail", "epub")
            null
        }
    }

    private fun extractCoverImage(): FetchResult? {
        ZipFile(file).use { zip ->
            val coverEntry = findCoverEntry(zip) ?: return null
            val bytes = zip.getInputStream(coverEntry).use { it.readBytes() }

            val buffer = Buffer()
            buffer.write(bytes)

            val mimeType = when {
                coverEntry.name.endsWith(".jpg", ignoreCase = true) ||
                        coverEntry.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                coverEntry.name.endsWith(".png", ignoreCase = true) -> "image/png"
                coverEntry.name.endsWith(".gif", ignoreCase = true) -> "image/gif"
                coverEntry.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> null
            }

            return SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = mimeType,
                dataSource = DataSource.DISK
            )
        }
    }

    private fun findCoverEntry(zip: ZipFile): java.util.zip.ZipEntry? {
        val entries = zip.entries().toList()

        for (entry in entries) {
            val name = entry.name.lowercase()
            if (name.contains("cover") && isImageFile(name)) {
                return entry
            }
        }

        val opfEntry = entries.find { it.name.endsWith(".opf", ignoreCase = true) }
        if (opfEntry != null) {
            val coverHref = parseCoverFromOpf(zip, opfEntry)
            if (coverHref != null) {
                val basePath = opfEntry.name.substringBeforeLast('/', "")
                val fullPath = if (basePath.isEmpty()) coverHref else "$basePath/$coverHref"

                entries.find { it.name.equals(fullPath, ignoreCase = true) }?.let { return it }
                entries.find { it.name.equals(coverHref, ignoreCase = true) }?.let { return it }
                entries.find { it.name.endsWith(coverHref, ignoreCase = true) }?.let { return it }
            }
        }

        for (entry in entries) {
            val name = entry.name.lowercase()
            if (isImageFile(name) && (name.contains("oebps") || name.contains("images"))) {
                return entry
            }
        }

        return entries.find { isImageFile(it.name.lowercase()) }
    }

    private fun parseCoverFromOpf(zip: ZipFile, opfEntry: java.util.zip.ZipEntry): String? {
        return try {
            val content = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }

            val coverMetaRegex = """<meta[^>]*name\s*=\s*["']cover["'][^>]*content\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val coverMetaMatch = coverMetaRegex.find(content)
            val coverId = coverMetaMatch?.groupValues?.get(1)

            if (coverId != null) {
                val itemRegex = """<item[^>]*id\s*=\s*["']${Regex.escape(coverId)}["'][^>]*href\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                val itemMatch = itemRegex.find(content)
                itemMatch?.groupValues?.get(1)?.let { return it }

                val itemAltRegex = """<item[^>]*href\s*=\s*["']([^"']+)["'][^>]*id\s*=\s*["']${Regex.escape(coverId)}["']""".toRegex(RegexOption.IGNORE_CASE)
                val itemAltMatch = itemAltRegex.find(content)
                itemAltMatch?.groupValues?.get(1)?.let { return it }
            }

            val coverImageRegex = """<item[^>]*properties\s*=\s*["'][^"']*cover-image[^"']*["'][^>]*href\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val coverImageMatch = coverImageRegex.find(content)
            coverImageMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            ErrorReporter.warning(e, "parse_epub_opf", "epub")
            null
        }
    }

    private fun isImageFile(name: String): Boolean {
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".webp")
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.exists() || !data.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isEpub(MimeTypeUtil.getMimeType(data))) {
                return null
            }
            return EpubThumbnailFetcher(data, options)
        }
    }
}
