package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

/*
 * Plain models for the in-app PDF viewer. The platform hands back RectF, Uri and its own content
 * classes; those are converted at the renderer boundary so everything above it — the ViewModel and
 * its JVM unit tests, where android.jar is a stub — only ever sees these.
 *
 * Every coordinate is in PDF points (1/72 inch) in the page's own space, origin at the top-left.
 */

/** A page's size in points, as the renderer reports it. */
@Immutable
data class PdfPageSize(val width: Int, val height: Int)

/** A rectangle on a page, in points. */
@Immutable
data class PdfRectPt(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/** A link on a page: the areas that activate it, and where it goes. */
@Immutable
sealed interface PdfLink {
    val rects: List<PdfRectPt>

    /** A jump to another page of the same document. [page] is 0-based. */
    @Immutable
    data class GoTo(val page: Int, override val rects: List<PdfRectPt>) : PdfLink

    /** A URI outside the document, exactly as the document spells it. */
    @Immutable
    data class External(val url: String, override val rects: List<PdfRectPt>) : PdfLink
}

/** One occurrence of a search query: the page it is on and the rectangles it covers there. */
@Immutable
data class PdfSearchMatch(val page: Int, val rects: List<PdfRectPt>)

/** The size, in pixels, of a bitmap to render into. */
@Immutable
data class PdfRenderSize(val width: Int, val height: Int)
