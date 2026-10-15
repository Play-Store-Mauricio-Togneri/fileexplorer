package com.mauriciotogneri.fileexplorer.data.util

import java.util.Locale

/** Audio and video times, as the media viewer and Item Info show them. */
object MediaTimeFormatter {

    private const val HOUR_MS = 3_600_000L

    /**
     * [millis] as `mm:ss`, or `h:mm:ss` when [durationMs] — the length of the file it is a time
     * in — reaches an hour, so a position and its duration always share a shape and the text does
     * not change width as playback crosses the hour.
     */
    fun format(millis: Long, durationMs: Long): String {
        val totalSeconds = millis.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (durationMs >= HOUR_MS || hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
