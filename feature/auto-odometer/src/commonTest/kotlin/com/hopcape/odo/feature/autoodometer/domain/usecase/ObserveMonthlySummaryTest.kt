package com.hopcape.odo.feature.autoodometer.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Monthly window edges: trips exactly at the calendar-month start and at "now" both count. */
class ObserveMonthlySummaryTest {

    private val timeZone = TimeZone.UTC
    private val now = Instant.parse("2026-08-07T12:00:00Z")
    private val startOfMonth = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun countsOnlyTripsWithinTheCalendarMonth_bothBoundsInclusive() = runTest {
        // Exactly at the lower bound — counts.
        val atMonthStart = testTrip(
            id = "a",
            startedAt = startOfMonth,
            endedAt = startOfMonth + 30.minutes,
            distanceMeters = 10_000,
        )
        // One second before the lower bound — belongs to last month, doesn't count.
        val beforeMonth = testTrip(
            id = "b",
            startedAt = startOfMonth - 1.seconds,
            endedAt = startOfMonth,
            distanceMeters = 5_000,
        )
        // Exactly at the upper bound ("now") — counts.
        val atNow = testTrip(id = "c", startedAt = now, endedAt = now + 10.minutes, distanceMeters = 20_000)
        // One second after "now" — hasn't happened yet from the summary's point of view.
        val afterNow = testTrip(
            id = "d",
            startedAt = now + 1.seconds,
            endedAt = now + 10.minutes,
            distanceMeters = 1_000,
        )

        val trips = FakeTripRepository(initial = listOf(atMonthStart, beforeMonth, atNow, afterNow))
        val useCase = ObserveMonthlySummary(trips, FixedClock(now), timeZone)

        val summary = useCase(TEST_CAR)

        assertEquals(2, summary.tripCount)
        assertEquals(30, summary.totalDistance.km)
    }

    @Test
    fun noCountedTripsThisMonth_reportsZero() = runTest {
        val trips = FakeTripRepository(initial = emptyList())
        val useCase = ObserveMonthlySummary(trips, FixedClock(now), timeZone)

        val summary = useCase(TEST_CAR)

        assertEquals(0, summary.tripCount)
        assertEquals(0, summary.totalDistance.km)
    }
}
