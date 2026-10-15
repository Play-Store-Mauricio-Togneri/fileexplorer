package com.mauriciotogneri.fileexplorer.data.util

import androidx.media3.common.PlaybackException

/**
 * Returns true when a [PlaybackException] with [errorCode] means an audio or video file that
 * cannot be played on this device. These are expected, unactionable conditions (not bugs) and must
 * not be reported to crash analytics:
 *  - the file is missing or unreadable (deleted, unmounted, no permission)
 *  - the container is malformed or of a kind no extractor knows
 *  - a track uses a format this device cannot decode, or cannot decode at this size or bitrate
 *  - no decoder could be set up for it, which some devices do for formats they claim to support
 *
 * Matched by error code rather than by exception type or message, since Media3 sets the code for
 * every failure it raises and the messages embed the file's URI.
 */
internal fun isUnplayableMedia(errorCode: Int): Boolean = errorCode in UNPLAYABLE_MEDIA_CODES

private val UNPLAYABLE_MEDIA_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
)
