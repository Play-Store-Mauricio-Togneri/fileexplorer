package com.mauriciotogneri.fileexplorer.data.source

import android.graphics.Bitmap
import com.mauriciotogneri.fileexplorer.data.model.PdfLink
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import java.io.File

/** Opens PDF documents for the in-app viewer. */
interface PdfDocumentOpener {

    /** What documents this opener returns can do; see [PdfViewerMode]. */
    val mode: PdfViewerMode

    /**
     * Opens [file], unlocking it with [password] when one is given. The password is only ever
     * passed through to the renderer; nothing keeps it.
     *
     * @throws SecurityException when the document needs a password this call did not supply, or the
     *   one supplied is wrong. In [PdfViewerMode.VIEW_ONLY] a password-protected document always
     *   fails this way, whatever [password] is.
     * @throws java.io.IOException for a missing, unreadable, corrupted or non-PDF file.
     */
    fun open(file: File, password: String?): PdfDocumentSource
}

/**
 * An open PDF document. Not thread-safe, like the platform renderer behind it: callers confine
 * every call, [close] included, to a single thread at a time. Each call opens and closes the page
 * it needs before returning, so no page outlives a call.
 */
interface PdfDocumentSource {
    val pageCount: Int

    /** Size of page [index] in points. */
    fun pageSize(index: Int): PdfPageSize

    /**
     * Renders page [index] into [bitmap], which is erased to white first. A page point (x, y) lands
     * on bitmap pixel (x * [scale] - [offsetX], y * [scale] - [offsetY]); whatever falls outside the
     * bitmap is clipped. A whole page is [scale] = bitmap width / page width and no offset, while a
     * zoomed region shifts by the region's top-left corner at that scale.
     */
    fun render(index: Int, bitmap: Bitmap, scale: Float, offsetX: Float, offsetY: Float)

    /** Every occurrence of [query] on page [index], each as the rectangles it covers. */
    fun search(index: Int, query: String): List<List<PdfRectPt>>

    /** The links on page [index]. */
    fun links(index: Int): List<PdfLink>

    fun close()
}
