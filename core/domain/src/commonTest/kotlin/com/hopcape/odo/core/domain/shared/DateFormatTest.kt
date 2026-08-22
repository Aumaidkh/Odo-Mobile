package com.hopcape.odo.core.domain.shared

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormatTest {

    @Test
    fun morningReadsAsAm() {
        assertEquals("10:12:32 AM", formatTimeOfDay(LocalTime(10, 12, 32)))
    }

    @Test
    fun afternoonWrapsBackToTwelveHours() {
        assertEquals("1:05:09 PM", formatTimeOfDay(LocalTime(13, 5, 9)))
    }

    /** Both ends of the day are hour 12, not hour 0 — the case a plain `% 12` gets wrong. */
    @Test
    fun midnightAndNoonAreTwelve() {
        assertEquals("12:00:00 AM", formatTimeOfDay(LocalTime(0, 0, 0)))
        assertEquals("12:30:00 PM", formatTimeOfDay(LocalTime(12, 30, 0)))
    }

    @Test
    fun minutesAndSecondsArePaddedButTheHourIsNot() {
        assertEquals("9:05:07 AM", formatTimeOfDay(LocalTime(9, 5, 7)))
    }
}
