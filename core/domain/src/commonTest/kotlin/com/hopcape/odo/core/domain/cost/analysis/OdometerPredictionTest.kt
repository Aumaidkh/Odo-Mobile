package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OdometerPredictionTest {

    @Test
    fun projectsForwardFromTheLastReadingAtTheCarsOwnRate() {
        val prediction = OdometerPrediction.forToday(
            readings = listOf(
                reading(LocalDate(2026, 1, 1), km = 30_000),
                reading(LocalDate(2026, 3, 2), km = 33_000),
            ),
            today = LocalDate(2026, 3, 12),
        )

        // 3,000 km over 60 days is 50 a day; ten days past the last reading is 500 more.
        assertEquals(33_500, prediction?.km)
        assertTrue(prediction?.predicted == true)
    }

    @Test
    fun withNoReadings_thereIsNothingToPredict() {
        assertNull(OdometerPrediction.forToday(readings = emptyList(), today = LocalDate(2026, 3, 12)))
    }

    @Test
    fun withOneReading_itComesBackUnchangedAndUnflagged() {
        val prediction = OdometerPrediction.forToday(
            readings = listOf(reading(LocalDate(2026, 1, 1), km = 30_000)),
            today = LocalDate(2026, 3, 12),
        )

        assertEquals(30_000, prediction?.km)
        assertFalse(prediction?.predicted == true)
    }

    @Test
    fun aHistoryShorterThanTheMinimumImpliesNoRate() {
        val prediction = OdometerPrediction.forToday(
            readings = listOf(
                reading(LocalDate(2026, 3, 1), km = 30_000),
                reading(LocalDate(2026, 3, 8), km = 32_000),
            ),
            today = LocalDate(2026, 3, 20),
        )

        // Seven days is under MIN_DAYS_FOR_RATE, so 285 km/day never gets used.
        assertEquals(32_000, prediction?.km)
        assertFalse(prediction?.predicted == true)
    }

    @Test
    fun aReadingTakenTodayIsNotProjectedPastItself() {
        val prediction = OdometerPrediction.forToday(
            readings = listOf(
                reading(LocalDate(2026, 1, 1), km = 30_000),
                reading(LocalDate(2026, 3, 12), km = 34_000),
            ),
            today = LocalDate(2026, 3, 12),
        )

        assertEquals(34_000, prediction?.km)
        assertFalse(prediction?.predicted == true)
    }

    @Test
    fun aLongSilenceIsCappedRatherThanExtrapolated() {
        val prediction = OdometerPrediction.forToday(
            readings = listOf(
                reading(LocalDate(2024, 1, 1), km = 10_000),
                reading(LocalDate(2024, 12, 31), km = 40_000),
            ),
            today = LocalDate(2026, 3, 12),
        )

        // Over 80 km a day for 430 days would be 35,000 km ahead of the last reading.
        assertEquals(40_000 + OdometerPrediction.MAX_PROJECTION_KM, prediction?.km)
        assertTrue(prediction?.predicted == true)
    }

    @Test
    fun readingsEnteredOutOfOrderNeverProduceANegativeRate() {
        val perDay = OdometerPrediction.kmPerDay(
            listOf(
                reading(LocalDate(2026, 1, 1), km = 50_000),
                reading(LocalDate(2026, 3, 2), km = 49_000),
            ),
        )

        assertNull(perDay)
    }

    private fun reading(date: LocalDate, km: Int) = OdometerReading(
        logId = null,
        date = date,
        odometer = Distance.of(km).getOrNull()!!,
    )
}
