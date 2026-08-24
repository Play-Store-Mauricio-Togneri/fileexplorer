package com.mauriciotogneri.fileexplorer.data.util

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
 *    and is matched by [isUnreadableFile] instead. Matching both keeps what gets
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
