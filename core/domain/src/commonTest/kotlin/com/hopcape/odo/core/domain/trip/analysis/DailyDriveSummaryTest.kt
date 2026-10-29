package com.hopcape.odo.core.domain.trip.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyDriveSummaryTest {

    @Test
    fun noDrives_isNothing() = assertNull(DailyDriveSummary.of(emptyList(), 700))

    @Test
    fun twoDrives_sumAndRound() {
        // 18.2 km at ₹7.00/km = ₹127.40, shown as ₹127.
        assertEquals(DailyDriveSummary(2, 18, 12_700), DailyDriveSummary.of(listOf(12_000, 6_200), 700))
    }

    @Test
    fun noFuelPrice_leavesTheCostOut() =
        assertEquals(DailyDriveSummary(1, 5, null), DailyDriveSummary.of(listOf(5_400), null))
}
