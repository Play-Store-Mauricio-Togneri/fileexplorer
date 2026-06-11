package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates a PDF that [android.graphics.pdf.PdfRenderer]
 * cannot open or render. These are expected, unactionable conditions (not bugs)
 * and must not be reported to crash analytics:
 *  - [SecurityException] — encrypted / password-protected PDF
 *  - [IOException] — corrupted, truncated, or non-PDF file
 *  - [IllegalStateException] — the document opens but a page cannot be loaded
 *    (seen as "cannot load page" from PdfRenderer.openPage on malformed pages)
 *
 * All are matched by type rather than by message. PdfRenderer's native error
 * messages are not stable across Android versions or OEMs: the same corrupted-PDF
 * failure has been observed as both "Unable to load the document!" and "file not
 * in PDF format or corrupted". Every such failure from opening or rendering a PDF
 * is unactionable from the app's side (bad file or transient storage error), so
 * matching the type is both sufficient and resistant to message drift. The
 * renderer and page are never accessed after close here, so the only
 * IllegalStateException reachable is an unloadable page, not a use-after-close bug.
 */
internal fun isUnreadablePdf(e: Throwable): Boolean =
    e is SecurityException || e is IOException || e is IllegalStateException
