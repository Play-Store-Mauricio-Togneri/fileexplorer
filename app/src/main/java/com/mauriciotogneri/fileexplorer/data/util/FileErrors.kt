package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates a file whose bytes could not be read at all — typically deleted,
 * renamed, or on a volume unmounted between an existence check and the read, or one this process is
 * not allowed to open. These are expected, unactionable conditions (not bugs) and must not be
 * reported to crash analytics.
 *
 * The guard for every reader that goes through `java.io` rather than a framework decoder. All of
 * them surface the family as [IOException] (e.g. [java.io.FileNotFoundException], whose message
 * Android builds as `<absolute path>: open failed: ENOENT`):
 *  - [TextFilePreview], which opens the file with `File.inputStream()` and reads it straight into
 *    the buffer the viewer renders.
 *  - [CsvMetadataExtractor], [VCardMetadataExtractor] and [ICalendarMetadataExtractor], which open
 *    the file with `File.bufferedReader()` and stream it a line at a time. Each keeps its per-line
 *    parsing inside `runCatching`, so only the read can surface an [IOException] to the catch.
 *  - [androidx.exifinterface.media.ExifInterface], whose constructor opens the file immediately.
 *    Malformed or non-EXIF content does not throw: ExifInterface swallows it internally and simply
 *    exposes no attributes.
 *  - Coil, whose [coil3.decode.ImageSource] opens the file lazily, so the failure surfaces well
 *    after the fetch succeeded — the first read is [coil3.svg.SvgDecoder]'s sniff for SVG content
 *    while Coil selects a decoder.
 *
 * Matched by type because that is the only failure any of those paths surfaces for a file it cannot
 * open, and its wording embeds the path. On Coil's side the net also covers
 * [android.graphics.ImageDecoder.DecodeException], an [IOException] the image viewer's animated
 * decoder raises for corrupted GIF/WebP content, which is a bad-file condition and equally
 * unactionable (its pre-API-28 counterpart is matched by [isUndecodableImage]).
 *
 * Any other exception reaching a caller is unexpected and remains reportable: a defect in the
 * app's own handling of bytes it did read — an index miscalculated while splitting lines — is
 * never an [IOException]. Neither is the decode failure of a file that *was* read successfully;
 * [isUndecodableImage] covers that one — and, because AndroidSVG wraps a mid-parse [IOException]
 * in an `SVGParseException` that is not itself an [IOException], it covers a read that failed
 * inside the SVG parser too, which this predicate cannot see. A denied open is not in that set
 * and is deliberately suppressed: libcore reports `EACCES` as a [java.io.FileNotFoundException]
 * like any other, and matching by type is all a reader that only has to decide whether to report
 * needs. [isStorageUnavailable] is the counterpart that does read the errno, because a walk over
 * many files has to know which failures it may step over; nothing here needs that distinction.
 *
 * New callers must keep any other [IOException]-throwing work out of the guarded block, otherwise
 * a genuine bug would be silently swallowed instead of reported. This is the broadest net in the
 * package, so that constraint binds harder here than for its siblings.
 */
internal fun isUnreadableFile(e: Throwable): Boolean = e is IOException
