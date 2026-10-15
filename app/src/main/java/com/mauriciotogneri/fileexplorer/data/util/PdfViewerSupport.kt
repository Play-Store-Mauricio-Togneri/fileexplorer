package com.mauriciotogneri.fileexplorer.data.util

import android.os.Build
import android.os.ext.SdkExtensions
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.model.PdfRenderSize
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * What the in-app PDF viewer can do on this device.
 *
 * - [FULL]: the renderer exposes text search, link contents and password loading — the platform
 *   `PdfRenderer` from API 35, or `PdfRendererPreV` on API 31–34 once the S extension reaches 13.
 * - [VIEW_ONLY]: only the classic `PdfRenderer(ParcelFileDescriptor)`: pages, scrolling and zoom. A
 *   password-protected document cannot be opened at all, so it gets a dedicated message rather
 *   than a prompt whose answer could never be used.
 */
enum class PdfViewerMode { FULL, VIEW_ONLY }

/**
 * The PDF viewer's pure decisions, kept free of Android types so they stay JVM-unit-testable the
 * same way [MimeTypeUtil.isViewableImage] does: the SDK level and the extension version are passed
 * in, and only [currentMode] reads them from the device.
 */
object PdfViewerSupport {

    /** First S-extension version that ships `PdfRendererPreV` and `LoadParams`. */
    const val MIN_S_EXTENSION_FOR_PRE_V = 13

    /** Largest zoom the viewer allows, and so the densest region it ever renders. */
    const val MAX_ZOOM = 4f

    /**
     * Longest side of any bitmap the viewer renders. 4096 is the lowest common GPU max texture
     * size: a larger bitmap renders fine and then uploads blank (see ImageViewerScreen).
     */
    const val MAX_BITMAP_SIDE = 4096

    /** Pixel budget per bitmap: 8 MP, 32 MB in ARGB_8888 — above any phone viewport. */
    const val MAX_BITMAP_PIXELS = 8L * 1024 * 1024

    /**
     * Tallest page shape the viewer lays out, as height over width. Compose cannot represent a
     * layout height past ~262k px, so an extreme page (a crafted `[0 0 1 1000]` MediaBox) would
     * crash the list; anything taller than this is shown cut off at this height instead. Real
     * receipts and long scans stay well under it on any phone or tablet width.
     */
    const val MAX_PAGE_ASPECT = 50

    private const val BYTES_PER_PIXEL = 4
    private const val MIN_CACHE_BYTES = 8L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    private const val CACHE_HEAP_FRACTION = 8

    private val ALLOWED_LINK_SCHEMES = setOf("http", "https", "mailto")
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*$")

    fun mode(sdkInt: Int, sExtensionVersion: Int): PdfViewerMode = when {
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> PdfViewerMode.FULL
        sdkInt >= Build.VERSION_CODES.S && sExtensionVersion >= MIN_S_EXTENSION_FOR_PRE_V ->
            PdfViewerMode.FULL
        else -> PdfViewerMode.VIEW_ONLY
    }

    /** [mode] for this device. `SdkExtensions` only exists from API 30, so older devices pass -1. */
    fun currentMode(): PdfViewerMode {
        val sExtension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)
        } else {
            -1
        }
        return mode(Build.VERSION.SDK_INT, sExtension)
    }

    /**
     * Whether a link a document carries may be handed to another app. A PDF is untrusted input,
     * so only web and mail links leave the viewer: `javascript:`, `file:`, `content:`, `intent:`
     * and every other scheme are dropped. A URL without a scheme is dropped too, since nothing
     * says what it would resolve to.
     */
    fun isAllowedExternalLink(url: String): Boolean {
        val separator = url.indexOf(':')
        if (separator <= 0) return false
        val scheme = url.substring(0, separator)
        if (!URI_SCHEME.matches(scheme)) return false
        if (url.substring(separator + 1).isBlank()) return false
        return scheme.lowercase() in ALLOWED_LINK_SCHEMES
    }

    /** The 1-based page number [input] names, or null unless it is a whole number in 1..[pageCount]. */
    fun parsePageNumber(input: String, pageCount: Int): Int? {
        val number = input.trim().toIntOrNull() ?: return null
        return number.takeIf { it in 1..pageCount }
    }

    /**
     * Bitmap size for a whole page [pageWidth] x [pageHeight] points drawn [targetWidth] pixels wide
     * at [zoom], kept within [MAX_BITMAP_SIDE] and [MAX_BITMAP_PIXELS] with the aspect ratio intact.
     */
    fun renderSize(pageWidth: Int, pageHeight: Int, targetWidth: Int, zoom: Float): PdfRenderSize {
        val width = targetWidth.coerceAtLeast(1) * zoom.coerceIn(1f, MAX_ZOOM)
        val aspect = pageHeight.coerceAtLeast(1).toFloat() / pageWidth.coerceAtLeast(1)
        return fitWithin(width, width * aspect)
    }

    /** [width] x [height] shrunk, aspect ratio intact, until it fits both bitmap caps. */
    fun fitWithin(width: Float, height: Float): PdfRenderSize {
        var factor = 1f
        val longest = max(width, height)
        if (longest > MAX_BITMAP_SIDE) {
            factor = MAX_BITMAP_SIDE / longest
        }
        val pixels = (width * factor).toDouble() * (height * factor)
        if (pixels > MAX_BITMAP_PIXELS) {
            factor *= sqrt(MAX_BITMAP_PIXELS / pixels).toFloat()
        }
        return PdfRenderSize(
            width = floor(width * factor).toInt().coerceIn(1, MAX_BITMAP_SIDE),
            height = floor(height * factor).toInt().coerceIn(1, MAX_BITMAP_SIDE)
        )
    }

    /**
     * [size] with its height cut to [MAX_PAGE_ASPECT] times its width. The cap is taken in `Long`
     * because a very wide page overflows it in `Int`, and a wrapped cap cuts the height to a small or
     * negative value.
     */
    fun layoutPageSize(size: PdfPageSize): PdfPageSize {
        val cap = size.width.toLong() * MAX_PAGE_ASPECT
        return size.copy(height = size.height.toLong().coerceAtMost(cap).toInt())
    }

    /** Bytes a rendered bitmap of [size] holds, which is what the page cache is budgeted in. */
    fun byteCount(size: PdfRenderSize): Long = size.width.toLong() * size.height * BYTES_PER_PIXEL

    /** Page-cache budget for a heap of [maxMemory] bytes: an eighth of it, within 8–64 MB. */
    fun cacheBudgetBytes(maxMemory: Long): Long =
        (maxMemory / CACHE_HEAP_FRACTION).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
}
