package com.mauriciotogneri.fileexplorer.data.util

import org.xml.sax.SAXException

/**
 * Returns true when [e] indicates image data a decoder could not turn into a bitmap,
 * even though the file's type is a supported, decodable image format: a corrupted,
 * truncated, zero-byte, or misnamed file (e.g. text saved as `.jpg`). The full-screen
 * viewer only opens files that pass [MimeTypeUtil.isViewableImage] and already shows
 * an error UI for this, so it is an expected, unactionable condition (not a bug) and
 * must not be reported to crash analytics. Three of the viewer's decoders raise it:
 *  - [coil3.decode.BitmapFactoryDecoder], as an [IllegalStateException] when
 *    `BitmapFactory` returns null. Coil reaches for it below API 29; from API 29 a still
 *    image goes through [coil3.decode.StaticImageDecoder] and the same corrupt JPEG or PNG
 *    arrives as [android.graphics.ImageDecoder.DecodeException] instead, matched by
 *    [isUnreadableFile].
 *  - [coil3.gif.GifDecoder], as an [IllegalStateException] when `Movie.decodeStream`
 *    returns null or a zero-sized frame. AppImageLoader registers it only below API 28,
 *    where [coil3.gif.AnimatedImageDecoder] is unavailable; from API 28 the same
 *    corrupt GIF or animated WebP arrives as
 *    [android.graphics.ImageDecoder.DecodeException] and is matched by [isUnreadableFile]
 *    instead. Matching both keeps what gets reported independent of the API level the app
 *    happens to run on.
 *  - [coil3.svg.SvgDecoder], as AndroidSVG's `SVGParseException` — a [SAXException]
 *    subclass — for a `.svg`/`.svgz` whose XML the parser rejects. `SVG.getFromInputStream`
 *    is called without a catch, so it propagates verbatim.
 *
 * The two Coil decoders are matched by message, the SVG one by type. Unlike the native
 * media/PDF wording elsewhere in this package — which embeds the file path or varies by
 * OEM — Coil's strings are fixed library constants, making a message match both stable
 * and, crucially, narrow: a bare [IllegalStateException] check would also swallow
 * unrelated decoder failures (e.g. a source read after close) that remain worth
 * reporting. If a Coil upgrade changes the wording the report simply resurfaces, which
 * is visible rather than dangerous.
 *
 * AndroidSVG cannot be matched the same way, for two reasons. Its messages are built from
 * the markup that failed to parse (`Bad hex colour value: <val>`, `Invalid value for
 * "xml:space" attribute: <val>`), so they are file content rather than fixed constants —
 * there is no stable phrase to match on, and no way to enumerate the ones that mean a bad
 * file. And `SVGParseException` itself is unreferenceable here:
 * `com.caverock:androidsvg-aar` reaches the app only as a runtime-scoped transitive of
 * `coil-svg`, so it is absent from the compile classpath. [SAXException] is its public
 * supertype, ships with the platform, and is exact enough at the one call site this
 * guards: the image viewer's load path parses no other XML. It also covers
 * `SVGParseException("Stream error", …)`, the wrapper AndroidSVG puts around an
 * [java.io.IOException] raised mid-parse, which [isUnreadableFile] would otherwise miss
 * because the wrapper is not itself an [java.io.IOException].
 *
 * That content never reached analytics even before this predicate matched it: the call
 * site reports `it.scrubbed()`, and [scrubbed] drops the message and the whole cause chain
 * for every throwable. This branch is about not filing an unactionable bad-file condition
 * at all — it is not what keeps the markup out of the report, and must not be relied on
 * as though it were.
 */
internal fun isUndecodableImage(e: Throwable): Boolean =
    e is SAXException ||
        (e is IllegalStateException && e.message?.contains(NULL_BITMAP_MESSAGE) == true) ||
        (e is IllegalStateException && e.message?.contains(FAILED_GIF_MESSAGE) == true)

private const val NULL_BITMAP_MESSAGE = "BitmapFactory returned a null bitmap"
private const val FAILED_GIF_MESSAGE = "Failed to decode GIF."
