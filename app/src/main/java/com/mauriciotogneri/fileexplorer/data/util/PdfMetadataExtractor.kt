package com.mauriciotogneri.fileexplorer.data.util

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.mauriciotogneri.fileexplorer.data.model.PdfMetadata
import java.io.File

object PdfMetadataExtractor {

    fun extract(file: File): PdfMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    PdfMetadata(
                        pageCount = renderer.pageCount.takeIf { it > 0 }
                    )
                }
            }
        } catch (e: Exception) {
            // PdfRenderer throws for corrupted or password-protected PDFs. These
            // are expected, unactionable conditions and not worth reporting.
            if (!isUnreadablePdf(e)) {
                ErrorReporter.warning(e, "extract_pdf_metadata", "pdf")
            }
            null
        }
    }
}
