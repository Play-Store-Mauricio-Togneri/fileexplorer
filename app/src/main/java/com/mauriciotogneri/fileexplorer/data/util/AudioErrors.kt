package com.mauriciotogneri.fileexplorer.data.util

/**
 * Returns true when [e] indicates an audio file that
 * [android.media.MediaMetadataRetriever] cannot read. These are expected,
 * unactionable conditions (not bugs) and must not be reported to crash analytics:
 *  - [RuntimeException] whose message contains "setDataSource failed" — the native
 *    decoder rejected corrupted or unsupported content.
 *  - [IllegalArgumentException] — `setDataSource(String)` throws this when the path
 *    cannot be opened (file deleted or volume unmounted after the existence check,
 *    or an I/O error).
 *
 * The [IllegalArgumentException] is matched by type rather than message because the
 * native wording embeds the file path and is not stable across Android versions or
 * OEMs (e.g. "<path> does not exist", "couldn't open <path>"), and `setDataSource`
 * is the only call in these fetchers/extractors that throws it. New callers must
 * keep any other [IllegalArgumentException]-throwing logic out of the guarded block,
 * otherwise a genuine bug would be silently swallowed instead of reported.
 */
internal fun isUnreadableAudio(e: Throwable): Boolean =
    e is IllegalArgumentException ||
        (e is RuntimeException && e.message?.contains("setDataSource failed") == true)
