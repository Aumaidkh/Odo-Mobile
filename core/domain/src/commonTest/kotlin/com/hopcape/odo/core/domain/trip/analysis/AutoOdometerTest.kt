package com.hopcape.odo.core.domain.trip.analysis

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AutoOdometerTest {

    private fun reading(day: Int, km: Int) = OdometerReading(
        logId = null,
        date = LocalDate(2026, 1, day),
        odometer = Distance.of(km).getOrNull()!!,
    )

    private fun trip(
        id: String,
        day: Int,
        km: Double,
        status: TripStatus = TripStatus.RECORDED,
    ): Trip = Trip.create(
        id = TripId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        startedAt = instant(day, 8),
        endedAt = instant(day, 9),
        distance = TripDistance.of((km * 1000).roundToLong()),
        estimatedDistance = TripDistance.ZERO,
        mode = TripMode.GPS_ONLY,
        status = status,
    ).getOrNull()!!

    private fun instant(day: Int, hour: Int): Instant =
        Instant.parse("2026-01-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z")

    @Test
    fun calibration_fromACleanPair() {
        val readings = listOf(reading(1, 1_000), reading(10, 1_100))
        val trips = listOf(trip("t1", day = 5, km = 95.0))

        val calibration = AutoOdometer.calibration(readings, trips)

        assertTrue(calibration.confident)
        assertEquals(100.0 / 95.0, calibration.k, absoluteTolerance = 0.0001)
    }

    @Test
    fun calibration_ewmaSmoothsAcrossThreeReadings() {
        val readings = listOf(reading(1, 0), reading(10, 100), reading(20, 200))
        val trips = listOf(
            trip("t1", day = 5, km = 100.0), // A->B: rawK = 100/100 = 1.0
            trip("t2", day = 15, km = 80.0), // B->C: rawK = 100/80 = 1.25
        )

        val calibration = AutoOdometer.calibration(readings, trips)

        // smoothed = 0.3*1.25 + 0.7*1.0 = 1.075
        assertEquals(1.075, calibration.k, absoluteTolerance = 0.0001)
        assertTrue(calibration.confident)
    }

    @Test
    fun calibration_clampsAtTheHighEnd() {
        val readings = listOf(reading(1, 0), reading(10, 150))
        val trips = listOf(trip("t1", day = 5, km = 100.0)) // rawK = 1.5

        assertEquals(1.10, AutoOdometer.calibration(readings, trips).k, absoluteTolerance = 0.0001)
    }

    @Test
    fun calibration_clampsAtTheLowEnd() {
        val readings = listOf(reading(1, 0), reading(10, 50))
        val trips = listOf(trip("t1", day = 5, km = 100.0)) // rawK = 0.5

        assertEquals(0.95, AutoOdometer.calibration(readings, trips).k, absoluteTolerance = 0.0001)
    }

    @Test
    fun calibration_fewerThanTwoReadings_isDefaultAndNotConfident() {
        assertEquals(Calibration(1.0, false), AutoOdometer.calibration(emptyList(), emptyList()))
        assertEquals(Calibration(1.0, false), AutoOdometer.calibration(listOf(reading(1, 0)), emptyList()))
    }

    @Test
    fun calibration_excludesUncountedTrips() {
        val readings = listOf(reading(1, 0), reading(10, 100))
        val trips = listOf(
            trip("recorded", day = 5, km = 100.0, status = TripStatus.RECORDED),
            trip("needs-confirmation", day = 6, km = 500.0, status = TripStatus.NEEDS_CONFIRMATION),
            trip("rejected", day = 7, km = 300.0, status = TripStatus.REJECTED),
        )

        // If the uncounted trips diluted the sum, k would be nowhere near 1.0.
        assertEquals(1.0, AutoOdometer.calibration(readings, trips).k, absoluteTolerance = 0.0001)
    }

    @Test
    fun derived_withNoCountedTripsSinceAnchor_neverGoesBelowIt() {
        val anchor = reading(1, 5_000)
        val tripsSince = listOf(trip("t1", day = 2, km = 50.0, status = TripStatus.NEEDS_CONFIRMATION))

        val derived = AutoOdometer.derived(anchor, tripsSince, Calibration(k = 1.0, confident = true))

        assertEquals(5_000, derived.km)
    }

    @Test
    fun derived_appliesTheCalibrationRatioToCountedTrips() {
        val anchor = reading(1, 5_000)
        val tripsSince = listOf(trip("t1", day = 2, km = 100.0, status = TripStatus.RECORDED))

        val derived = AutoOdometer.derived(anchor, tripsSince, Calibration(k = 1.03, confident = true))

        assertEquals(5_103, derived.km) // 5000 + 1.03*100 = 5103.0
    }

    @Test
    fun current_withNoReadingsAtAll_isNull() {
        assertNull(AutoOdometer.current(emptyList(), emptyList()))
    }

    @Test
    fun current_oneReadingAndNoTrips_returnsTheReadingUnchanged() {
        val readings = listOf(reading(1, 500))

        assertEquals(500, AutoOdometer.current(readings, emptyList())?.km)
    }

    @Test
    fun current_oneReadingPlusACountedTripAfterIt_addsTheTripOnTop() {
        // Only one manual reading, so calibration falls back to DEFAULT_K = 1.0 — the
        // result is exactly the anchor plus the trip's raw distance.
        val readings = listOf(reading(1, 500))
        val trips = listOf(trip("t1", day = 2, km = 5.0))

        assertEquals(505, AutoOdometer.current(readings, trips)?.km)
    }

    @Test
    fun current_reAnchorsOnANewerManualReadingAndDropsEarlierTrips() {
        // 500 km on day 0, a 5 km trip on day 1, then a manual log of 510 km on day 2 —
        // the day-1 trip is already absorbed into the 510 km reading. A day-3 trip must be
        // the only thing added on top of the new anchor.
        val readings = listOf(reading(1, 500), reading(3, 510))
        val trips = listOf(
            trip("absorbed", day = 2, km = 5.0),
            trip("new", day = 4, km = 2.0),
        )

        assertEquals(512, AutoOdometer.current(readings, trips)?.km)
    }

    @Test
    fun current_includesATripOnTheSameDayAsTheAnchor() {
        // OdometerReading has no time-of-day, so a same-day trip is deliberately counted —
        // the common case is a fresh reading followed by a same-day drive.
        val readings = listOf(reading(1, 500))
        val trips = listOf(trip("same-day", day = 1, km = 20.0))

        assertEquals(520, AutoOdometer.current(readings, trips)?.km)
    }

    @Test
    fun current_excludesARejectedTripAfterTheAnchor() {
        val readings = listOf(reading(1, 500))
        val trips = listOf(trip("rejected", day = 2, km = 20.0, status = TripStatus.REJECTED))

        assertEquals(500, AutoOdometer.current(readings, trips)?.km)
    }

    @Test
    fun current_anchorsOnTheHighestKnownReading_notMerelyTheNewestDated() {
        // Real service history at 45,000 km, then a mistaken newer-dated onboarding
        // baseline of 500 km (e.g. a re-onboard, or a sync pull that landed after
        // onboarding already ran locally). The anchor must stay 45,000 — the same floor
        // OdometerTimeline.validate would enforce on a same-day service log entry — so a
        // same-day trip adds on top of the real figure instead of the mistaken one.
        val readings = listOf(reading(1, 45_000), reading(5, 500))
        val trips = listOf(trip("t1", day = 5, km = 5.0))

        assertEquals(45_005, AutoOdometer.current(readings, trips)?.km)
    }

    @Test
    fun current_aWellFormedHistoryStillPicksTheNewestReadingAsAnchor() {
        // When km and date agree (the normal case), the two selection rules coincide.
        val readings = listOf(reading(1, 500), reading(5, 510))
        val trips = listOf(trip("t1", day = 6, km = 5.0))

        assertEquals(515, AutoOdometer.current(readings, trips)?.km)
    }

    @Test
    fun drift_flagsWhenTheLatestPairDivergesFromTheSmoothedRatio() {
        val readings = listOf(reading(1, 0), reading(10, 100), reading(20, 200))
        val trips = listOf(
            trip("t1", day = 5, km = 100.0), // A->B: rawK = 1.0
            trip("t2", day = 15, km = 80.0), // B->C: rawK = 1.25 (the latest pair)
        )

        val drift = AutoOdometer.drift(readings, trips)

        checkNotNull(drift)
        assertEquals(1.25, drift.rawK, absoluteTolerance = 0.0001)
        assertEquals(1.075, drift.smoothedK, absoluteTolerance = 0.0001)
        assertTrue(drift.divergence > 0.05)
    }

    @Test
    fun drift_isNullWhenThereIsOnlyOnePair() {
        val readings = listOf(reading(1, 0), reading(10, 100))
        val trips = listOf(trip("t1", day = 5, km = 100.0))

        assertNull(AutoOdometer.drift(readings, trips))
    }

    @Test
    fun drift_isNullWithFewerThanTwoReadings() {
        assertNull(AutoOdometer.drift(listOf(reading(1, 0)), emptyList()))
    }

    @Test
    fun calibration_absorbsASimulatedThreePercentDashOverRead() {
        // The dash shows 103 km between two manual readings; GPS-tracked trips in the same
        // window sum to 100 km — the dash is reading 3% high.
        val readings = listOf(reading(1, 0), reading(10, 103))
        val trips = listOf(trip("t1", day = 5, km = 100.0))

        val calibration = AutoOdometer.calibration(readings, trips)
        assertEquals(1.03, calibration.k, absoluteTolerance = 0.0001)

        // A new 100 km GPS-tracked trip after the anchor should predict what the dash would
        // show for it too: 103, not 100 — the ratio absorbed the bias.
        val newTrip = trip("t2", day = 15, km = 100.0)
        val derived = AutoOdometer.derived(reading(10, 103), listOf(newTrip), calibration)
        // Not 203 (anchor + raw km with no calibration) — the ratio absorbed the 3% bias.
        assertEquals(206, derived.km)
    }
}
