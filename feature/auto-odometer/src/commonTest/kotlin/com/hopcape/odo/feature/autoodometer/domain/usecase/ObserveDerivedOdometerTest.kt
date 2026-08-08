package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.trip.model.TripId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * [ObserveDerivedOdometer] wired to [com.hopcape.odo.core.domain.trip.analysis.AutoOdometer],
 * with a simulated 3% dash over-read — the dash shows 103 km between two manual
 * readings while GPS-tracked trips in the same window sum to 100 km (same fixture
 * `AutoOdometerTest.calibration_absorbsASimulatedThreePercentDashOverRead` uses).
 */
class ObserveDerivedOdometerTest {

    private val timeZone = TimeZone.UTC

    @Test
    fun threePercentDashOverRead_scalesTheNewTripUp_insteadOfANaiveOneToOneSum() = runTest {
        // Dash: 1000km on Jan 1 -> 1103km on Jan 10 (103km on the dash).
        val reading1 = testReading(LocalDate(2026, 1, 1), 1_000)
        val reading2 = testReading(LocalDate(2026, 1, 10), 1_103)

        // GPS-tracked trip inside that window: only 100km actually driven -> k = 103/100 = 1.03.
        val calibrationTrip = testTrip(
            id = "calibration",
            startedAt = Instant.parse("2026-01-05T08:00:00Z"),
            endedAt = Instant.parse("2026-01-05T09:00:00Z"),
            distanceMeters = 100_000,
        )

        // The trip being shown on the trip-logged screen: a fresh 100km GPS-tracked drive
        // after the last manual reading.
        val targetTrip = testTrip(
            id = "target",
            startedAt = Instant.parse("2026-01-15T08:00:00Z"),
            endedAt = Instant.parse("2026-01-15T09:00:00Z"),
            distanceMeters = 100_000,
        )

        val serviceLogs = FakeServiceLogRepository(readings = listOf(reading1, reading2))
        val trips = FakeTripRepository(initial = listOf(calibrationTrip, targetTrip))
        val useCase = ObserveDerivedOdometer(serviceLogs, trips, timeZone)

        val derived = useCase(TEST_CAR, targetTrip.id).first()

        checkNotNull(derived)
        // Anchor is 1103 (the newer manual reading). "Before" excludes the target trip.
        assertEquals(1_103, derived.before.km)
        // 1103 + 1.03 x 100 = 1206 -- not 1203, which is what a naive 1:1 sum would give.
        assertEquals(1_206, derived.current.km)
    }

    @Test
    fun noManualReadingToAnchorOn_isNull() = runTest {
        val trip = testTrip(
            id = "t1",
            startedAt = Instant.parse("2026-01-15T08:00:00Z"),
            endedAt = Instant.parse("2026-01-15T09:00:00Z"),
            distanceMeters = 10_000,
        )
        val serviceLogs = FakeServiceLogRepository(readings = emptyList())
        val trips = FakeTripRepository(initial = listOf(trip))
        val useCase = ObserveDerivedOdometer(serviceLogs, trips, timeZone)

        assertNull(useCase(TEST_CAR, trip.id).first())
    }

    @Test
    fun unknownTripId_isNull() = runTest {
        val reading = testReading(LocalDate(2026, 1, 1), 1_000)
        val serviceLogs = FakeServiceLogRepository(readings = listOf(reading))
        val trips = FakeTripRepository(initial = emptyList())
        val useCase = ObserveDerivedOdometer(serviceLogs, trips, timeZone)

        assertNull(useCase(TEST_CAR, TripId("missing")).first())
    }
}
