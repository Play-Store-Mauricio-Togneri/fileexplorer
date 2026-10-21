package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import java.util.TimeZone

/**
 * When the app may ask for a Play Store rating. The goal is rating quality, not volume: only users
 * who have kept the app for a while, come back to it and get things done with it are asked, and
 * at most twice.
 *
 * Google never says whether its review dialog appeared or whether the user rated, so a launched
 * flow counts as an ask either way. The second ask exists to recover a first one that Google's
 * quota silently swallowed.
 */
object ReviewEligibility {
    const val MIN_INSTALL_AGE_MILLIS = 14 * DAY_MILLIS
    const val MIN_ACTIVE_DAYS = 5
    const val MIN_ACTIONS = 15
    const val RETRY_GAP_MILLIS = 120 * DAY_MILLIS
    const val MAX_ASKS = 2

    /**
     * The attempt that is due now — 1 for the first ask, 2 for the retry — or null when the user
     * should not be asked. A clock that reads earlier than the install or the first ask means
     * not yet, never "long ago".
     */
    fun dueAttempt(record: ReviewPromptRecord, nowMillis: Long, installedAtMillis: Long): Int? {
        if (record.askCount >= MAX_ASKS) return null
        if (nowMillis - installedAtMillis < MIN_INSTALL_AGE_MILLIS) return null
        if (record.activeDays < MIN_ACTIVE_DAYS || record.actionCount < MIN_ACTIONS) return null
        if (record.askCount > 0 && nowMillis - record.firstAskAt < RETRY_GAP_MILLIS) return null

        return record.askCount + 1
    }

    /**
     * The calendar day [millis] falls on in [timeZone], counted from the epoch. `java.time` would
     * say this directly, but it needs API 26 and this app supports 24 without desugaring.
     */
    fun localEpochDay(millis: Long, timeZone: TimeZone): Long {
        return Math.floorDiv(millis + timeZone.getOffset(millis), DAY_MILLIS)
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
