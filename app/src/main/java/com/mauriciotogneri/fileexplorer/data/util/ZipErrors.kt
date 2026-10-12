package com.mauriciotogneri.fileexplorer.data.util

import java.io.EOFException
import java.util.zip.ZipException

/**
 * Returns true when [e] indicates a file that [java.util.zip.ZipFile] cannot open
 * as a valid ZIP container. EPUB and Office (docx/xlsx/pptx) documents are ZIP
 * archives; a corrupted, truncated, or non-archive file makes [java.util.zip.ZipFile]
 * throw [ZipException] (e.g. "zip END header not found", "invalid CEN header").
 * These are expected, unactionable conditions (not bugs) and must not be reported
 * to crash analytics.
 *
 * An archive whose central directory is intact but whose entry data ends early opens
 * fine and only fails once the entry is inflated, with [EOFException] ("Unexpected end
 * of ZLIB input stream") rather than [ZipException]. That is malformed content too.
 *
 * Matched by type because [java.util.zip.ZipFile] throws [ZipException] only for
 * malformed ZIP content. A genuine I/O failure (e.g. the file removed mid-read)
 * surfaces as a plain [java.io.IOException] rather than a [ZipException], so it
 * remains reportable.
 */
internal fun isUnreadableZip(e: Throwable): Boolean = e is ZipException || e is EOFException
