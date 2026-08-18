package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The pattern lookup is faked throughout: [android.text.format.DateFormat.getBestDateTimePattern] is
 * a framework call, unavailable in a JVM unit test. What is under test is which of the two patterns
 * a timestamp gets and how the result is rendered — the ICU data behind the pattern is Android's
 * problem, not this class's.
 */
class ShortDateFormatterTest {

    private val HOURS_23 = 23 * 60 * 60 * 1000L

    private val fakePatterns: (Locale, String) -> String = { _, skeleton ->
        when (skeleton) {
            "dMMM" -> "d MMM"
            else -> "d MMM y"
        }
    }

    private fun formatter(
        now: Long,
        locale: Locale = Locale.UK,
        patternProvider: (Locale, String) -> String = fakePatterns
    ) = ShortDateFormatter(locale = locale, patternProvider = patternProvider, now = { now })

    private fun timestamp(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }

    @Test
    fun `a date in the current year is formatted without the year`() {
        val formatter = formatter(now = timestamp(2026, Calendar.AUGUST, 17))

        assertEquals("7 Aug", formatter.format(timestamp(2026, Calendar.AUGUST, 7)))
    }

    @Test
    fun `a date in an earlier year is formatted with the year`() {
        val formatter = formatter(now = timestamp(2026, Calendar.AUGUST, 17))

        assertEquals("7 Aug 2019", formatter.format(timestamp(2019, Calendar.AUGUST, 7)))
    }

    @Test
    fun `a date later this year is formatted without the year`() {
        val formatter = formatter(now = timestamp(2026, Calendar.JANUARY, 2))

        assertEquals("30 Dec", formatter.format(timestamp(2026, Calendar.DECEMBER, 30)))
    }

    /**
     * The boundary is the calendar year, not an interval: two days apart across New Year still means
     * two different years, which is exactly when the year is worth showing.
     */
    @Test
    fun `a date days old but in the previous year carries the year`() {
        val formatter = formatter(now = timestamp(2026, Calendar.JANUARY, 2))

        assertEquals("31 Dec 2025", formatter.format(timestamp(2025, Calendar.DECEMBER, 31)))
    }

    @Test
    fun `the first instant of the current year is formatted without the year`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val formatter = formatter(now = timestamp(2026, Calendar.JUNE, 1))

        assertEquals("1 Jan", formatter.format(calendar.timeInMillis))
    }

    @Test
    fun `patterns are asked for in the locale the formatter was built with`() {
        val requested = mutableListOf<Pair<Locale, String>>()

        formatter(
            now = timestamp(2026, Calendar.AUGUST, 17),
            locale = Locale.GERMANY,
            patternProvider = { locale, skeleton ->
                requested.add(locale to skeleton)
                "d MMM"
            }
        )

        assertEquals(listOf(Locale.GERMANY, Locale.GERMANY), requested.map { it.first })
        assertEquals(setOf("dMMM", "dMMMy"), requested.map { it.second }.toSet())
    }

    /**
     * The locale decides the order of day and month, so a pattern that puts the month first has to
     * come out month first. Asserted through the pattern rather than through a month's spelling,
     * which moves with the JDK's CLDR data.
     */
    @Test
    fun `a month-first pattern renders month first`() {
        val formatter = formatter(
            now = timestamp(2026, Calendar.AUGUST, 17),
            locale = Locale.US,
            patternProvider = { _, skeleton -> if (skeleton == "dMMM") "MMM d" else "MMM d, y" }
        )

        assertEquals("Aug 7", formatter.format(timestamp(2026, Calendar.AUGUST, 7)))
        assertEquals("Aug 7, 2019", formatter.format(timestamp(2019, Calendar.AUGUST, 7)))
    }

    @Test
    fun `the year in use is the one the clock reports, not the one the JVM runs at`() {
        val formatter = formatter(now = timestamp(2019, Calendar.AUGUST, 17))

        // Same timestamp as the "earlier year" case above; only the clock moved.
        assertEquals("7 Aug", formatter.format(timestamp(2019, Calendar.AUGUST, 7)))
    }

    /**
     * `File.lastModified` reports 0 when it cannot be read, and the item info screen already answers
     * "-" for that. A row must not turn it into a date the file does not have.
     */
    @Test
    fun `a timestamp at or before the epoch formats as nothing`() {
        val formatter = formatter(now = timestamp(2026, Calendar.AUGUST, 17))

        assertEquals("", formatter.format(0L))
        assertEquals("", formatter.format(-1L))
    }

    /**
     * The instance is remembered per locale and a time zone change is not a configuration change, so
     * one outlives the zone it was built in. Both halves — the year comparison and the rendering —
     * have to follow the new zone, or a timestamp in the first hours of a new local year picks the
     * no-year pattern and renders as the previous December.
     */
    @Test
    fun `a time zone change after construction moves the rendered date`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val formatter = formatter(now = timestamp(2026, Calendar.AUGUST, 17))
            val instant = timestamp(2026, Calendar.AUGUST, 7) - HOURS_23

            assertEquals("6 Aug", formatter.format(instant))

            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-14"))

            assertEquals("7 Aug", formatter.format(instant))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `formatting the same timestamp twice gives the same result`() {
        val formatter = formatter(now = timestamp(2026, Calendar.AUGUST, 17))
        val moment = timestamp(2026, Calendar.MARCH, 3)

        assertTrue(formatter.format(moment) == formatter.format(moment))
    }
}
