package com.mauriciotogneri.fileexplorer.data.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.size.Dimension
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

class PdfThumbnailFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            renderPdfThumbnail()
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_pdf_thumbnail", "pdf")
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
                    val targetWidth = options.size.width.pxOrElse { 120 }
                    val scale = targetWidth.toFloat() / page.width
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val buffer = Buffer()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
                    bitmap.recycle()

                    return SourceResult(
                        source = ImageSource(buffer, options.context),
                        mimeType = "image/png",
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
            return PdfThumbnailFetcher(data, options)
        }
    }
}

private fun Dimension.pxOrElse(default: () -> Int): Int {
    return if (this is Dimension.Pixels) px else default()
}
