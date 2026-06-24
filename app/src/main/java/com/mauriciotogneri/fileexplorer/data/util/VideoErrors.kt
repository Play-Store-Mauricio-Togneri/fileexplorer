package com.mauriciotogneri.fileexplorer.data.util

/**
 * Returns true when [e] indicates a video file that
 * [android.media.MediaMetadataRetriever] cannot read. These are expected,
 * unactionable conditions (not bugs) and must not be reported to crash analytics:
 *  - [RuntimeException] whose message contains "setDataSource failed" — the native
 *    decoder rejected corrupted or unsupported content.
 *  - [IllegalArgumentException] — `setDataSource(String)` throws this when the path
 *    cannot be opened (file deleted or volume unmounted after the existence check,
 *    or an I/O error).
 *  - [IllegalStateException] — the native layer throws this (often with an empty
 *    message) when no retriever is available, e.g. the media extractor service is
 *    unreachable or resource-starved; some OEM ROMs also throw it, instead of the
 *    documented "setDataSource failed" RuntimeException, for content the decoder
 *    cannot read.
 *
 * [IllegalArgumentException] and [IllegalStateException] are matched by type rather
 * than message: the native wording embeds the file path or is empty and is not
 * stable across Android versions or OEMs (e.g. "<path> does not exist", "couldn't
 * open <path>"). Inside these fetchers/extractors only MediaMetadataRetriever calls
 * throw them, so matching the type is safe. New callers must keep any other
 * [IllegalArgumentException]- or [IllegalStateException]-throwing logic out of the
 * guarded block, otherwise a genuine bug would be silently swallowed instead of
 * reported.
 */
internal fun isUnreadableVideo(e: Throwable): Boolean =
    e is IllegalArgumentException ||
        e is IllegalStateException ||
        (e is RuntimeException && e.message?.contains("setDataSource failed") == true)
