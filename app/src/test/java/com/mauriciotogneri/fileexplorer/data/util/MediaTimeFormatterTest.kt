package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTimeFormatterTest {

    @Test
    fun `under an hour is minutes and seconds, both padded`() {
        assertEquals("00:00", MediaTimeFormatter.format(0, 60_000))
        assertEquals("01:23", MediaTimeFormatter.format(83_000, 2_718_000))
        assertEquals("45:18", MediaTimeFormatter.format(2_718_000, 2_718_000))
    }

    @Test
    fun `partial seconds are dropped rather than rounded`() {
        assertEquals("59:59", MediaTimeFormatter.format(3_599_999, 3_599_999))
    }

    @Test
    fun `a file of an hour or more shows hours on every time in it`() {
        assertEquals("0:01:23", MediaTimeFormatter.format(83_000, 3_600_000))
        assertEquals("1:02:03", MediaTimeFormatter.format(3_723_000, 3_723_000))
    }

    @Test
    fun `a position past an hour shows hours even when the duration is unknown`() {
        assertEquals("1:00:00", MediaTimeFormatter.format(3_600_000, 0))
    }

    @Test
    fun `a negative time shows as zero`() {
        assertEquals("00:00", MediaTimeFormatter.format(-5_000, 60_000))
    }
}
