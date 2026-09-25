package com.mauriciotogneri.fileexplorer.data.source

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.RenderParams
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import com.mauriciotogneri.fileexplorer.data.model.PdfLink
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerSupport
import java.io.File

/**
 * [PdfDocumentOpener] over the platform's `android.graphics.pdf`, picking the richest renderer
 * [mode] allows on this device:
 *
 * - API 35+: [PdfRenderer] with [LoadParams] for passwords, plus search and links.
 * - API 31–34 with S extension 13: [PdfRendererPreV], the same capabilities backported.
 * - Everything else, or [PdfViewerMode.VIEW_ONLY]: the classic [PdfRenderer], pages only.
 *
 * [mode] is a parameter so a test can hold a FULL-capable device to the view-only path; production
 * always passes [PdfViewerSupport.currentMode]. The SDK checks below repeat what that decided because
 * lint only credits a guard it can see next to the call.
 */
class AndroidPdfDocumentOpener(
    override val mode: PdfViewerMode = PdfViewerSupport.currentMode()
) : PdfDocumentOpener {

    override fun open(file: File, password: String?): PdfDocumentSource {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            return openDescriptor(descriptor, password)
        } catch (e: Throwable) {
            // A renderer owns the descriptor only once it is constructed; one whose constructor
            // threw (a password needed, a corrupted file) leaves it open behind it.
            try {
                descriptor.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun openDescriptor(
        descriptor: ParcelFileDescriptor,
        password: String?
    ): PdfDocumentSource {
        if (mode == PdfViewerMode.FULL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                return Api35PdfDocument(descriptor, password)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >=
                PdfViewerSupport.MIN_S_EXTENSION_FOR_PRE_V
            ) {
                return PreVPdfDocument(descriptor, password)
            }
        }
        return LegacyPdfDocument(descriptor)
    }
}

private fun transform(scale: Float, offsetX: Float, offsetY: Float): Matrix = Matrix().apply {
    setScale(scale, scale)
    postTranslate(-offsetX, -offsetY)
}

private fun RectF.toPt(): PdfRectPt = PdfRectPt(left, top, right, bottom)

/** The classic renderer: every API level the app supports, pages only. */
private class LegacyPdfDocument(descriptor: ParcelFileDescriptor) : PdfDocumentSource {
    private val renderer = PdfRenderer(descriptor)

    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(index: Int): PdfPageSize =
        renderer.openPage(index).use { PdfPageSize(it.width, it.height) }

    override fun render(index: Int, bitmap: Bitmap, scale: Float, offsetX: Float, offsetY: Float) {
        renderer.openPage(index).use { page ->
            // PDF pages with no background paint over whatever the bitmap holds, and a fresh
            // bitmap is transparent — drawn over a dark surface the text would vanish.
            bitmap.eraseColor(Color.WHITE)
            page.render(
                bitmap,
                null,
                transform(scale, offsetX, offsetY),
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
        }
    }

    override fun search(index: Int, query: String): List<List<PdfRectPt>> = emptyList()

    override fun links(index: Int): List<PdfLink> = emptyList()

    override fun close() {
        renderer.close()
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class Api35PdfDocument(
    descriptor: ParcelFileDescriptor,
    password: String?
) : PdfDocumentSource {
    private val renderer = if (password == null) {
        PdfRenderer(descriptor)
    } else {
        PdfRenderer(descriptor, LoadParams.Builder().setPassword(password).build())
    }

    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(index: Int): PdfPageSize =
        renderer.openPage(index).use { PdfPageSize(it.width, it.height) }

    override fun render(index: Int, bitmap: Bitmap, scale: Float, offsetX: Float, offsetY: Float) {
        renderer.openPage(index).use { page ->
            bitmap.eraseColor(Color.WHITE)
            page.render(
                bitmap,
                null,
                transform(scale, offsetX, offsetY),
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
        }
    }

    override fun search(index: Int, query: String): List<List<PdfRectPt>> =
        renderer.openPage(index).use { page ->
            page.searchText(query).map { match -> match.bounds.map { it.toPt() } }
        }

    override fun links(index: Int): List<PdfLink> =
        renderer.openPage(index).use { page ->
            page.getGotoLinks().map { link ->
                PdfLink.GoTo(page = link.destination.pageNumber, rects = link.bounds.map { it.toPt() })
            } + page.getLinkContents().map { link ->
                PdfLink.External(url = link.uri.toString(), rects = link.bounds.map { it.toPt() })
            }
        }

    override fun close() {
        renderer.close()
    }
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
private class PreVPdfDocument(
    descriptor: ParcelFileDescriptor,
    password: String?
) : PdfDocumentSource {
    private val renderer = if (password == null) {
        PdfRendererPreV(descriptor)
    } else {
        PdfRendererPreV(descriptor, LoadParams.Builder().setPassword(password).build())
    }

    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(index: Int): PdfPageSize =
        renderer.openPage(index).use { PdfPageSize(it.width, it.height) }

    override fun render(index: Int, bitmap: Bitmap, scale: Float, offsetX: Float, offsetY: Float) {
        renderer.openPage(index).use { page ->
            bitmap.eraseColor(Color.WHITE)
            page.render(
                bitmap,
                null,
                transform(scale, offsetX, offsetY),
                RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).build()
            )
        }
    }

    override fun search(index: Int, query: String): List<List<PdfRectPt>> =
        renderer.openPage(index).use { page ->
            page.searchText(query).map { match -> match.bounds.map { it.toPt() } }
        }

    override fun links(index: Int): List<PdfLink> =
        renderer.openPage(index).use { page ->
            page.getGotoLinks().map { link ->
                PdfLink.GoTo(page = link.destination.pageNumber, rects = link.bounds.map { it.toPt() })
            } + page.getLinkContents().map { link ->
                PdfLink.External(url = link.uri.toString(), rects = link.bounds.map { it.toPt() })
            }
        }

    override fun close() {
        renderer.close()
    }
}
