package com.mauriciotogneri.fileexplorer.data.util

/**
 * Returns true when [e] indicates an audio file that
 * [android.media.MediaMetadataRetriever] cannot read. These are expected,
 * unactionable conditions (not bugs) and must not be reported to crash analytics.
 *
 * MediaMetadataRetriever throws a [RuntimeException] whose message contains
 * "setDataSource failed" for corrupted, unsupported, or inaccessible audio files.
 */
internal fun isUnreadableAudio(e: Throwable): Boolean =
    e is RuntimeException && e.message?.contains("setDataSource failed") == true
