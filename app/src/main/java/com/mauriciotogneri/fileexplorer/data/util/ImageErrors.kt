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
