package com.hopcape.odo.core.domain.servicelog.model

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentReadingTest {

    private fun reading(id: String?, year: Int, month: Int, day: Int, km: Int) = OdometerReading(
        logId = id?.let(::ServiceLogId),
        date = LocalDate(year, month, day),
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    @Test
    fun theMostRecentlyDatedReading_wins() {
        val readings = listOf(
            reading(null, 2026, 1, 1, 45_000),
            reading("log-1", 2026, 6, 15, 52_000),
            reading("log-2", 2024, 3, 12, 30_000),
        )

        assertEquals(52_000, readings.currentReading()?.odometer?.km)
    }

    @Test
    fun aSameDayTie_goesToTheHigherKm() {
        val readings = listOf(
            reading("log-1", 2026, 6, 15, 52_000),
            reading("log-2", 2026, 6, 15, 52_400),
        )

        assertEquals(52_400, readings.currentReading()?.odometer?.km)
    }

    @Test
    fun anEmptyTimeline_hasNoCurrentReading() {
        assertNull(emptyList<OdometerReading>().currentReading())
    }
}
