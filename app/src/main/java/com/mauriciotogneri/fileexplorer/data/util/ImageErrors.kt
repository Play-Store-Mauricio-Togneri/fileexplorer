package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates an image file that could not be read at all —
 * typically deleted, renamed, or on a volume unmounted between the existence check
 * and the read. These are expected, unactionable conditions (not bugs) and must not
 * be reported to crash analytics. Both readers of image bytes surface the family as
 * [IOException] (e.g. [java.io.FileNotFoundException]):
 *  - [androidx.exifinterface.media.ExifInterface], whose constructor opens the file
 *    immediately. Malformed or non-EXIF content does not throw: ExifInterface
 *    swallows it internally and simply exposes no attributes.
 *  - Coil, whose [coil.decode.ImageSource] opens the file lazily, so the failure
 *    surfaces well after the fetch succeeded — the first read is
 *    [coil.decode.SvgDecoder]'s sniff for SVG content while Coil selects a decoder.
 *
 * Matched by type because that is the only failure either path surfaces for a file
 * it cannot open, and its wording embeds the path. On Coil's side the net also
 * covers [android.graphics.ImageDecoder.DecodeException], an [IOException] the
 * viewer's animated decoder raises for corrupted GIF/WebP content, which is a
 * bad-file condition and equally unactionable (its pre-API-28 counterpart is matched
 * by [isUndecodableImage]). Any other exception reaching the
 * caller is unexpected and remains reportable; the decode failure of a file that
 * *was* read successfully is not matched here — [isUndecodableImage] covers it.
 */
internal fun isUnreadableImage(e: Throwable): Boolean = e is IOException

/**
 * Returns true when [e] indicates image data a decoder could not turn into a bitmap,
 * even though the file's type is a supported, decodable image format: a corrupted,
 * truncated, zero-byte, or misnamed file (e.g. text saved as `.jpg`). The full-screen
 * viewer only opens files that pass [MimeTypeUtil.isViewableImage] and already shows
 * an error UI for this, so it is an expected, unactionable condition (not a bug) and
 * must not be reported to crash analytics. Two of Coil's decoders raise it as an
 * [IllegalStateException]:
 *  - [coil.decode.BitmapFactoryDecoder], when `BitmapFactory` returns null.
 *  - [coil.decode.GifDecoder], when `Movie.decodeStream` returns null or a zero-sized
 *    frame. AppImageLoader registers it only below API 28, where
 *    [coil.decode.ImageDecoderDecoder] is unavailable; from API 28 the same corrupt
 *    GIF or animated WebP arrives as [android.graphics.ImageDecoder.DecodeException]
 *    and is matched by [isUnreadableImage] instead. Matching both keeps what gets
 *    reported independent of the API level the app happens to run on.
 *
 * Matched by message rather than type. Unlike the native media/PDF wording elsewhere
 * in this package — which embeds the file path or varies by OEM, and so is matched by
 * type — Coil's strings are fixed library constants, making a message match both
 * stable and, crucially, narrow: a bare [IllegalStateException] check would also
 * swallow unrelated decoder failures (e.g. a malformed SVG, or a source read after
 * close) that remain worth reporting. If a Coil upgrade changes the wording the report
 * simply resurfaces, which is visible rather than dangerous.
 */
internal fun isUndecodableImage(e: Throwable): Boolean {
    val message = (e as? IllegalStateException)?.message ?: return false
    return message.contains(NULL_BITMAP_MESSAGE) || message.contains(FAILED_GIF_MESSAGE)
}

private const val NULL_BITMAP_MESSAGE = "BitmapFactory returned a null bitmap"
private const val FAILED_GIF_MESSAGE = "Failed to decode GIF."
