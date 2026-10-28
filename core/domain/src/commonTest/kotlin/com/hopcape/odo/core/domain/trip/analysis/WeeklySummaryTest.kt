package com.hopcape.odo.core.domain.trip.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklySummaryTest {

    @Test
    fun allThreeParts() = assertEquals(WeeklySummary(142, 620, 14.1), WeeklySummary.of(listOf(100_000, 42_200), 620, 14.1))

    @Test
    fun tripsOnly_leavesTheRestOut() = assertEquals(WeeklySummary(142, null, null), WeeklySummary.of(listOf(142_000), null, null))

    @Test
    fun rateWithoutTrips_isStillARecap() = assertEquals(WeeklySummary(null, 620, null), WeeklySummary.of(emptyList(), 620, null))

    @Test
    fun nothingKnown_isNoRecap() = assertNull(WeeklySummary.of(emptyList(), null, null))
}
