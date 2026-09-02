package com.mauriciotogneri.fileexplorer.data.util

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubThumbnailFetcher(
    private val file: File,
    private val options: Options,
    diskCache: DiskCache?
) : Fetcher {

    // The cover is stored as the archive carries it, so one entry covers every size.
    private val thumbnailCache = ThumbnailDiskCache(diskCache, options, FILE_TYPE, file, variesWithSize = false)

    override suspend fun fetch(): FetchResult? {
        // The cover is stored as the archive holds it; which format that is depends on the entry
        // found, so leave detection to the decoder rather than record the type alongside it.
        thumbnailCache.read(mimeType = null)?.let { return it }

        return try {
            extractCoverImage()
        } catch (e: Exception) {
            // A corrupted or non-EPUB file makes ZipFile throw ZipException. These
            // are expected, unactionable conditions and not worth reporting.
            if (!isUnreadableZip(e)) {
                ErrorReporter.warning(e.scrubbed(), "extract_epub_thumbnail", FILE_TYPE)
            }
            null
        }
    }

    private fun extractCoverImage(): FetchResult? {
        ZipFile(file).use { zip ->
            val coverEntry = findCoverEntry(zip) ?: return null
            val bytes = zip.getInputStream(coverEntry).use { it.readBytes() }

            val buffer = Buffer()
            buffer.write(bytes)

            // A copy, because writing consumes the buffer and Coil still has to decode it.
            thumbnailCache.write(buffer.copy())

            val mimeType = when {
                coverEntry.name.endsWith(".jpg", ignoreCase = true) ||
                        coverEntry.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                coverEntry.name.endsWith(".png", ignoreCase = true) -> "image/png"
                coverEntry.name.endsWith(".gif", ignoreCase = true) -> "image/gif"
                coverEntry.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> null
            }

            return SourceFetchResult(
                source = ImageSource(buffer, options.fileSystem),
                mimeType = mimeType,
                dataSource = DataSource.DISK
            )
        }
    }

    private fun findCoverEntry(zip: ZipFile): ZipEntry? {
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

    private fun parseCoverFromOpf(zip: ZipFile, opfEntry: ZipEntry): String? {
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
            // A corrupt EPUB entry can throw ZipException during inflation; expected, not worth reporting.
            if (!isUnreadableZip(e)) {
                ErrorReporter.warning(e.scrubbed(), "parse_epub_opf", FILE_TYPE)
            }
            null
        }
    }

    private fun isImageFile(name: String): Boolean {
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".webp")
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val file = data.toFileOrNull() ?: return null
            if (!file.exists() || !file.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isEpub(MimeTypeUtil.getMimeType(file))) {
                return null
            }
            return EpubThumbnailFetcher(file, options, imageLoader.diskCache)
        }
    }
}

private const val FILE_TYPE = ThumbnailFileType.EPUB
