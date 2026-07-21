package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates an image that
 * [androidx.exifinterface.media.ExifInterface] cannot open for reading. These are
 * expected, unactionable conditions (not bugs) and must not be reported to crash
 * analytics.
 *
 * ExifInterface's constructor opens the file immediately and throws [IOException]
 * (e.g. [java.io.FileNotFoundException]) when the path cannot be opened — typically
 * the file was deleted or its volume unmounted between the existence check and the
 * read. Malformed or non-EXIF image content does not throw: ExifInterface swallows
 * it internally and simply exposes no attributes. Matched by type because that is
 * the only failure ExifInterface surfaces for an unreadable file; any other
 * exception reaching the caller is unexpected and remains reportable.
 */
internal fun isUnreadableImage(e: Throwable): Boolean = e is IOException

/**
 * Returns true when [e] indicates image data that a decoder could not turn into a
 * bitmap, even though the file's type is a supported, decodable image format. This
 * is the failure Coil's [coil.decode.BitmapFactoryDecoder] raises when
 * `BitmapFactory` returns null: a corrupted, truncated, zero-byte, or misnamed file
 * (e.g. text saved as `.jpg`). The full-screen viewer only opens files that pass
 * [MimeTypeUtil.isViewableImage] and already shows an error UI for this, so it is an
 * expected, unactionable condition (not a bug) and must not be reported to crash
 * analytics.
 *
 * Matched by message rather than type. Unlike the native media/PDF wording elsewhere
 * in this package — which embeds the file path or varies by OEM, and so is matched by
 * type — Coil's string is a fixed library constant, making a message match both
 * stable and, crucially, narrow: a bare [IllegalStateException] check would also
 * swallow unrelated decoder failures (e.g. SVG or animated formats in the viewer's
 * loader) that remain worth reporting. If a Coil upgrade changes the wording the
 * report simply resurfaces, which is visible rather than dangerous.
 */
internal fun isUndecodableImage(e: Throwable): Boolean =
    e is IllegalStateException &&
        e.message?.contains("BitmapFactory returned a null bitmap") == true
