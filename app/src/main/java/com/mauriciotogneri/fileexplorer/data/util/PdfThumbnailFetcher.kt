package com.mauriciotogneri.fileexplorer.data.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import android.os.ParcelFileDescriptor
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

class PdfThumbnailFetcher(
    private val file: File,
    private val options: Options,
    diskCache: DiskCache?
) : Fetcher {

    // The page is fitted inside the box requested, so an entry only covers requests up to that size.
    private val thumbnailCache = ThumbnailDiskCache(diskCache, options, FILE_TYPE, file, variesWithSize = true)

    override suspend fun fetch(): FetchResult? {
        thumbnailCache.read(MIME_TYPE)?.let { return it }

        return try {
            renderPdfThumbnail()
        } catch (e: Exception) {
            // PdfRenderer throws for corrupted or password-protected PDFs. These
            // are expected, unactionable conditions and not worth reporting.
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e.scrubbed(), "extract_pdf_thumbnail", FILE_TYPE)
            }
            null
        }
    }

    private fun renderPdfThumbnail(): FetchResult? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { pdfRenderer ->
                if (pdfRenderer.pageCount == 0) {
                    return null
                }

                pdfRenderer.openPage(0).use { page ->
                    // Fitted inside the requested box rather than scaled to its width alone, so
                    // the page's longest side never exceeds the box's. ThumbnailDiskCache records
                    // an entry as covering the box it was extracted for, and a width-only scale
                    // over a page taller than the box would render less than that record claims —
                    // a later request the bytes cannot satisfy would then be served upscaled
                    // instead of re-extracted, for as long as the entry lived.
                    val scale = minOf(
                        options.thumbnailWidth().toFloat() / page.width,
                        options.thumbnailHeight().toFloat() / page.height
                    )
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val buffer = Buffer()
                    val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
                    bitmap.recycle()

                    // A failed compress leaves the buffer empty or truncated, and caching that
                    // commits a broken thumbnail to disk which is then served on every later request
                    // until the file's modification time changes — where before the disk cache it
                    // cost one bad load.
                    if (compressed && buffer.size > 0) {
                        // A copy, because writing consumes the buffer and Coil still has to decode it.
                        thumbnailCache.write(buffer.copy())
                    }

                    return SourceFetchResult(
                        source = ImageSource(buffer, options.fileSystem),
                        mimeType = MIME_TYPE,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val file = data.toFileOrNull() ?: return null
            if (!file.exists() || !file.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isPdf(MimeTypeUtil.getMimeType(file))) {
                return null
            }
            return PdfThumbnailFetcher(file, options, imageLoader.diskCache)
        }
    }
}

private const val FILE_TYPE = ThumbnailFileType.PDF
private const val MIME_TYPE = "image/png"
