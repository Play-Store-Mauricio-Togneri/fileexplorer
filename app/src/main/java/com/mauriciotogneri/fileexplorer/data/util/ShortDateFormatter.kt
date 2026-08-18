package com.mauriciotogneri.fileexplorer.data.util

import android.text.format.DateFormat
import androidx.compose.runtime.Stable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats a timestamp for a list row: day and month for the current year, day, month and year for
 * any other. Without the year a file from 2019 and one from last week read identically, which on a
 * screen people use to find old files hides the very fact they came for.
 *
 * Both patterns come from the locale rather than being written out here, so the parts land in the
 * order the language uses: "7 Aug" in en-GB, "Aug 7" in en-US, "8月7日" in ja.
 *
 * Not thread-safe: [SimpleDateFormat] mutates an internal calendar while formatting. Instances are
 * built per locale and used from composition, which is single-threaded.
 *
 * @param patternProvider resolves a locale and an ICU skeleton to a pattern. Defaults to the
 * framework's ICU data; unit tests pass known patterns instead, since the framework call is not
 * available off-device.
 * @param now the current time, injectable so the year boundary can be tested.
 */
@Stable
class ShortDateFormatter(
    locale: Locale,
    patternProvider: (Locale, String) -> String = { targetLocale, skeleton ->
        DateFormat.getBestDateTimePattern(targetLocale, skeleton)
    },
    private val now: () -> Long = System::currentTimeMillis
) {
    private val currentYearFormat = SimpleDateFormat(patternProvider(locale, CURRENT_YEAR_SKELETON), locale)
    private val otherYearFormat = SimpleDateFormat(patternProvider(locale, OTHER_YEAR_SKELETON), locale)

    /**
     * Returns an empty string for a timestamp at or before the epoch: `File.lastModified` reports 0
     * when it cannot be read — a dangling symlink, or a file removed between the listing and the
     * stat — and rendering that as "1 Jan 1970" states a modification date the file does not have.
     * The item info screen already answers "-" for the same value.
     */
    fun format(timestamp: Long): String {
        if (timestamp <= 0) return ""

        // A fresh Calendar each call rather than a cached one: it costs a lookup of the default
        // time zone, and in exchange a device that crosses a time zone or a day boundary while the
        // list is open formats against the new one instead of the one this instance was built with.
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now()
        val currentYear = calendar.get(Calendar.YEAR)
        calendar.timeInMillis = timestamp

        val format = if (calendar.get(Calendar.YEAR) == currentYear) currentYearFormat else otherYearFormat

        // Both formats captured the default zone when they were built, and this instance outlives a
        // zone change: it is remembered per locale, and changing zone is not a configuration change.
        // Without this the year is chosen in the new zone and the date rendered in the old one, which
        // near New Year prints "31 Dec" with no year at all — the case this class exists to prevent.
        format.timeZone = calendar.timeZone

        return format.format(Date(timestamp))
    }

    companion object {
        /** Day and month, ordered by the locale. */
        private const val CURRENT_YEAR_SKELETON = "dMMM"

        /** Day, month and year, ordered by the locale. */
        private const val OTHER_YEAR_SKELETON = "dMMMy"
    }
}
