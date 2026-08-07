package com.mauriciotogneri.fileexplorer.data.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

class PdfThumbnailFetcher(
    private val file: File,
    private val options: Options,
    diskCache: DiskCache?
) : Fetcher {

    // The page is rendered at the width requested, so an entry only covers requests up to that size.
    private val thumbnailCache = ThumbnailDiskCache(diskCache, options, FILE_TYPE, file, variesWithSize = true)

    override suspend fun fetch(): FetchResult? {
        thumbnailCache.read(MIME_TYPE)?.let { return it }

        return try {
            renderPdfThumbnail()
        } catch (e: Exception) {
            // PdfRenderer throws for corrupted or password-protected PDFs. These
            // are expected, unactionable conditions and not worth reporting.
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e, "extract_pdf_thumbnail", FILE_TYPE)
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
                    val targetWidth = options.thumbnailWidth()
                    val scale = targetWidth.toFloat() / page.width
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val buffer = Buffer()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
                    bitmap.recycle()

                    // A copy, because writing consumes the buffer and Coil still has to decode it.
                    thumbnailCache.write(buffer.copy())

                    return SourceResult(
                        source = ImageSource(buffer, options.context),
                        mimeType = MIME_TYPE,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.exists() || !data.canRead()) {
                return null
            }
            if (!MimeTypeUtil.isPdf(MimeTypeUtil.getMimeType(data))) {
                return null
            }
            return PdfThumbnailFetcher(data, options, imageLoader.diskCache)
        }
    }
}

private const val FILE_TYPE = ThumbnailFileType.PDF
private const val MIME_TYPE = "image/png"
