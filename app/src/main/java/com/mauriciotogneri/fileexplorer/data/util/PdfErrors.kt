package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates a PDF that [android.graphics.pdf.PdfRenderer]
 * cannot open or render. These are expected, unactionable conditions (not bugs)
 * and must not be reported to crash analytics:
 *  - [SecurityException] — encrypted / password-protected PDF
 *  - [IOException] — corrupted, truncated, or non-PDF file
 *
 * Both are matched by type rather than by message. PdfRenderer's native error
 * messages are not stable across Android versions or OEMs: the same corrupted-PDF
 * failure has been observed as both "Unable to load the document!" and "file not
 * in PDF format or corrupted". Every IOException from opening or rendering a PDF
 * is unactionable from the app's side (bad file or transient storage error), so
 * matching the type is both sufficient and resistant to message drift.
 */
internal fun isUnreadablePdf(e: Throwable): Boolean =
    e is SecurityException || e is IOException
