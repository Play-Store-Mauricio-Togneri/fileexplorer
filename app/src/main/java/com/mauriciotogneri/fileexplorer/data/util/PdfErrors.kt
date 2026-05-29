package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates a PDF that [android.graphics.pdf.PdfRenderer]
 * cannot open. These are expected, unactionable conditions (not bugs) and must
 * not be reported to crash analytics:
 *  - [SecurityException] ("password required or incorrect password") — encrypted PDF
 *  - [IOException] ("Unable to load the document!") — corrupted / malformed PDF
 *
 * The [SecurityException] is matched by type rather than message because that is
 * the only condition under which PdfRenderer throws it, and the native message
 * wording is not guaranteed to be stable across Android versions or OEMs.
 */
internal fun isUnreadablePdf(e: Throwable): Boolean =
    e is SecurityException ||
        (e is IOException && e.message == "Unable to load the document!")
