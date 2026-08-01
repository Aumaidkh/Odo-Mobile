package com.hopcape.odo.core.domain.cost.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CostWindowTest {

    private val today = LocalDate(2026, 8, 1)

    @Test
    fun endingOn_startsTheDayAfterTheMonthsBack() {
        val window = CostWindow.endingOn(today, months = 3)

        assertEquals(LocalDate(2026, 5, 2), window.start)
        assertEquals(today, window.end)
    }

    @Test
    fun lengthCountsBothEnds() {
        assertEquals(1, CostWindow(today, today).lengthInDays)
        assertEquals(92, CostWindow.endingOn(today, months = 3).lengthInDays)
    }

    @Test
    fun containsIsInclusiveOfBothEnds() {
        val window = CostWindow.endingOn(today, months = 3)

        assertTrue(window.start in window)
        assertTrue(window.end in window)
        assertTrue(LocalDate(2026, 6, 15) in window)
        assertFalse(LocalDate(2026, 5, 1) in window)
        assertFalse(LocalDate(2026, 8, 2) in window)
    }

    @Test
    fun previous_isTheSameLengthAndDoesNotOverlap() {
        val window = CostWindow.endingOn(today, months = 3)
        val previous = window.previous()

        assertEquals(window.lengthInDays, previous.lengthInDays)
        assertEquals(LocalDate(2026, 5, 1), previous.end)
        assertEquals(LocalDate(2026, 1, 30), previous.start)
        assertFalse(previous.end in window)
    }

    @Test
    fun aWindowCannotEndBeforeItStarts() {
        assertFailsWith<IllegalArgumentException> {
            CostWindow(start = today, end = LocalDate(2026, 7, 31))
        }
    }

    @Test
    fun aWindowSpansAtLeastOneMonth() {
        assertFailsWith<IllegalArgumentException> { CostWindow.endingOn(today, months = 0) }
    }
}
