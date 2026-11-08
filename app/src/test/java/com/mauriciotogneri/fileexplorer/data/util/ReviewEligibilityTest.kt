package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The thresholds are written out as numbers rather than read from [ReviewEligibility]'s constants,
 * so a change to who gets asked has to change a test as well.
 */
class ReviewEligibilityTest {

    private val installedAt = 1_700_000_000_000L
    private val installAgeMet = installedAt + 14 * DAY_MILLIS
    private val engaged = ReviewPromptRecord(activeDays = 5, actionCount = 15)

    private fun retryRecord(firstAskAt: Long, actionCount: Int = 15) = ReviewPromptRecord(
        askCount = 1,
        firstAskAt = firstAskAt,
        activeDays = 5,
        actionCount = actionCount
    )

    private fun localMillis(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(year, month, day, hour, minute)

        return calendar.timeInMillis
    }

    @Test
    fun `an install one millisecond short of fourteen days old is not asked`() {
        assertNull(ReviewEligibility.dueAttempt(engaged, installAgeMet - 1, installedAt))
    }

    @Test
    fun `an install exactly fourteen days old is asked for the first time`() {
        assertEquals(1, ReviewEligibility.dueAttempt(engaged, installAgeMet, installedAt))
    }

    @Test
    fun `four active days are not enough`() {
        val record = engaged.copy(activeDays = 4)

        assertNull(ReviewEligibility.dueAttempt(record, installAgeMet, installedAt))
    }

    @Test
    fun `five active days are enough`() {
        val record = engaged.copy(activeDays = 5)

        assertEquals(1, ReviewEligibility.dueAttempt(record, installAgeMet, installedAt))
    }

    @Test
    fun `fourteen actions are not enough`() {
        val record = engaged.copy(actionCount = 14)

        assertNull(ReviewEligibility.dueAttempt(record, installAgeMet, installedAt))
    }

    @Test
    fun `fifteen actions are enough`() {
        val record = engaged.copy(actionCount = 15)

        assertEquals(1, ReviewEligibility.dueAttempt(record, installAgeMet, installedAt))
    }

    @Test
    fun `a retry one millisecond short of 120 days after the first ask is not due`() {
        val firstAskAt = installAgeMet
        val record = retryRecord(firstAskAt)

        assertNull(ReviewEligibility.dueAttempt(record, firstAskAt + 120 * DAY_MILLIS - 1, installedAt))
    }

    @Test
    fun `a retry 120 days after the first ask is the second attempt`() {
        val firstAskAt = installAgeMet
        val record = retryRecord(firstAskAt)

        assertEquals(2, ReviewEligibility.dueAttempt(record, firstAskAt + 120 * DAY_MILLIS, installedAt))
    }

    // Asking resets the counters, so a retry that only waited out its gap is not one the user earned.
    @Test
    fun `a retry still needs the engagement thresholds`() {
        val firstAskAt = installAgeMet
        val record = retryRecord(firstAskAt, actionCount = 14)

        assertNull(ReviewEligibility.dueAttempt(record, firstAskAt + 365 * DAY_MILLIS, installedAt))
    }

    @Test
    fun `there is no third ask, however long ago the first one was`() {
        val record = engaged.copy(askCount = 2, firstAskAt = installAgeMet)

        assertNull(ReviewEligibility.dueAttempt(record, installAgeMet + 3650 * DAY_MILLIS, installedAt))
    }

    @Test
    fun `a clock earlier than the install does not read as an old install`() {
        assertNull(ReviewEligibility.dueAttempt(engaged, installedAt - 365 * DAY_MILLIS, installedAt))
    }

    @Test
    fun `a clock earlier than the first ask does not read as a finished wait`() {
        val firstAskAt = installAgeMet + 365 * DAY_MILLIS
        val record = retryRecord(firstAskAt)

        assertNull(ReviewEligibility.dueAttempt(record, firstAskAt - 10 * DAY_MILLIS, installedAt))
    }

    @Test
    fun `a new UTC day starts at UTC midnight`() {
        val utc = TimeZone.getTimeZone("UTC")

        assertEquals(20_540L, ReviewEligibility.localEpochDay(20_541L * DAY_MILLIS - 1, utc))
        assertEquals(20_541L, ReviewEligibility.localEpochDay(20_541L * DAY_MILLIS, utc))
    }

    // Plain division rounds towards zero and would put the last instant before the epoch on day 0.
    @Test
    fun `the instant before the epoch falls on the day before it`() {
        assertEquals(-1L, ReviewEligibility.localEpochDay(-1L, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `local midnight starts a new day in a zone ahead of UTC`() {
        val zone = TimeZone.getTimeZone("GMT+05:30")

        val lateEvening = localMillis(zone, 2026, Calendar.MARCH, 29, 23, 59)
        val pastMidnight = localMillis(zone, 2026, Calendar.MARCH, 30, 0, 1)

        assertEquals(20_541L, ReviewEligibility.localEpochDay(lateEvening, zone))
        assertEquals(20_542L, ReviewEligibility.localEpochDay(pastMidnight, zone))
    }

    @Test
    fun `just after UTC midnight it is still the previous day in a zone behind UTC`() {
        val utc = TimeZone.getTimeZone("UTC")
        val zone = TimeZone.getTimeZone("GMT-03:00")

        // 01:00 UTC on 20 October 2026 is 22:00 on the 19th, three hours behind.
        val instant = localMillis(utc, 2026, Calendar.OCTOBER, 20, 1, 0)

        assertEquals(20_746L, ReviewEligibility.localEpochDay(instant, utc))
        assertEquals(20_745L, ReviewEligibility.localEpochDay(instant, zone))
    }

    @Test
    fun `the 23-hour day clocks spring forward on is one day between its neighbours`() {
        val madrid = TimeZone.getTimeZone("Europe/Madrid")

        val days = listOf(
            localMillis(madrid, 2026, Calendar.MARCH, 28, 23, 59),
            localMillis(madrid, 2026, Calendar.MARCH, 29, 0, 1),
            localMillis(madrid, 2026, Calendar.MARCH, 29, 23, 59),
            localMillis(madrid, 2026, Calendar.MARCH, 30, 0, 1)
        ).map { ReviewEligibility.localEpochDay(it, madrid) }

        assertEquals(listOf(20_540L, 20_541L, 20_541L, 20_542L), days)
    }

    @Test
    fun `the 25-hour day clocks fall back on is one day between its neighbours`() {
        val madrid = TimeZone.getTimeZone("Europe/Madrid")

        val days = listOf(
            localMillis(madrid, 2026, Calendar.OCTOBER, 24, 23, 59),
            localMillis(madrid, 2026, Calendar.OCTOBER, 25, 0, 1),
            localMillis(madrid, 2026, Calendar.OCTOBER, 25, 23, 59),
            localMillis(madrid, 2026, Calendar.OCTOBER, 26, 0, 1)
        ).map { ReviewEligibility.localEpochDay(it, madrid) }

        assertEquals(listOf(20_750L, 20_751L, 20_751L, 20_752L), days)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
