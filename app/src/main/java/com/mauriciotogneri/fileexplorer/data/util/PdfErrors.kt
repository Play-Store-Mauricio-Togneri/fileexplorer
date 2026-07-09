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
 *  - [IllegalArgumentException] — the file descriptor is not seekable, so the
 *    path is not a regular file (a FIFO, socket, or character device named
 *    `*.pdf`), or it was invalidated before the renderer read it. PdfRenderer's
 *    constructor `lseek`s the descriptor and converts any `ErrnoException` into
 *    "file descriptor not seekable".
 *
 * All are matched by type rather than by message. PdfRenderer's native error
 * messages are not stable across Android versions or OEMs: the same corrupted-PDF
 * failure has been observed as both "Unable to load the document!" and "file not
 * in PDF format or corrupted". Every such failure from opening or rendering a PDF
 * is unactionable from the app's side (bad file or transient storage error), so
 * matching the type is both sufficient and resistant to message drift. Callers
 * nest the renderer and page in `use` blocks (correct close ordering) and wrap
 * only PdfRenderer calls, so the only IllegalStateException reachable is an
 * unloadable page, not a use-after-close bug, and the only IllegalArgumentException
 * reachable is an unseekable descriptor: page indices are bounds-checked against
 * `pageCount`, and bitmaps are created with non-zero dimensions and the ARGB_8888
 * config that `render` requires. New callers must keep any other
 * [IllegalArgumentException]- or [IllegalStateException]-throwing logic out of the
 * guarded block, otherwise a genuine bug would be silently swallowed instead of
 * reported.
 */
internal fun isUnreadablePdf(e: Throwable): Boolean =
    e is SecurityException ||
        e is IOException ||
        e is IllegalStateException ||
        e is IllegalArgumentException
