package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.trip.model.TripStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [AcknowledgeTrip] advancing the marker, and [RejectTrip] dropping a trip out of both
 * [ObservePendingTripLogged] and [ObserveDerivedOdometer] — the reflow behaviour D4 and
 * the derived odometer both depend on.
 */
class MarkerAndRejectReflowTest {

    private val anchorDate = LocalDate(2026, 1, 1)
    private val anchorKm = 10_000
    private val trip1Start = Instant.parse("2026-01-02T08:00:00Z")
    private val trip1End = Instant.parse("2026-01-02T09:00:00Z")
    private val trip2Start = Instant.parse("2026-01-03T08:00:00Z")
    private val trip2End = Instant.parse("2026-01-03T09:00:00Z")

    @Test
    fun acknowledgeTrip_advancesMarker_soPendingTripLoggedStopsSurfacingIt() = runTest {
        val trip1 = testTrip(id = "t1", startedAt = trip1Start, endedAt = trip1End, distanceMeters = 50_000)
        val trips = FakeTripRepository(initial = listOf(trip1))
        val settings = FakeAppSettingsRepository()
        val observePending = ObservePendingTripLogged(trips, settings)
        val acknowledge = AcknowledgeTrip(settings)

        assertEquals(trip1.id, observePending(TEST_CAR).first())

        val result = acknowledge(trip1.endedAt)
        assertTrue(result.isRight())
        assertEquals(trip1.endedAt, settings.settings.value.aoLastAckedTripEndedAt)

        assertNull(observePending(TEST_CAR).first())
    }

    @Test
    fun needsConfirmationTrip_stillSurfacesAsPending_soItCanBeReviewed() = runTest {
        val pending = testTrip(
            id = "t1",
            startedAt = trip1Start,
            endedAt = trip1End,
            distanceMeters = 5_000,
            status = TripStatus.NEEDS_CONFIRMATION,
        )
        val trips = FakeTripRepository(initial = listOf(pending))
        val settings = FakeAppSettingsRepository()
        val observePending = ObservePendingTripLogged(trips, settings)

        // A trip the validation ladder flagged must still reach the trip-logged screen —
        // otherwise the owner can never see it to confirm or reject it.
        assertEquals(pending.id, observePending(TEST_CAR).first())
    }

    @Test
    fun rejectTrip_dropsItOutOfPendingTripLogged() = runTest {
        val trip1 = testTrip(id = "t1", startedAt = trip1Start, endedAt = trip1End, distanceMeters = 50_000)
        val trips = FakeTripRepository(initial = listOf(trip1))
        val settings = FakeAppSettingsRepository()
        val observePending = ObservePendingTripLogged(trips, settings)
        val reject = RejectTrip(trips)

        assertEquals(trip1.id, observePending(TEST_CAR).first())

        reject(trip1.id)

        assertNull(observePending(TEST_CAR).first())
    }

    @Test
    fun rejectTrip_afterAcknowledgingAnEarlierTrip_dropsTheNewerOneOutOfPending() = runTest {
        val trip1 = testTrip(id = "t1", startedAt = trip1Start, endedAt = trip1End, distanceMeters = 50_000)
        val trip2 = testTrip(id = "t2", startedAt = trip2Start, endedAt = trip2End, distanceMeters = 30_000)
        val trips = FakeTripRepository(initial = listOf(trip1, trip2))
        val settings = FakeAppSettingsRepository()
        val observePending = ObservePendingTripLogged(trips, settings)
        val acknowledge = AcknowledgeTrip(settings)
        val reject = RejectTrip(trips)

        // The newest counted trip is t2.
        assertEquals(trip2.id, observePending(TEST_CAR).first())

        // Acknowledge only t1 — t2 should still be the pending one.
        acknowledge(trip1.endedAt)
        assertEquals(trip2.id, observePending(TEST_CAR).first())

        // Rejecting t2 leaves nothing newer than the t1 marker.
        reject(trip2.id)
        assertNull(observePending(TEST_CAR).first())
    }

    @Test
    fun rejectTrip_dropsItsDistanceOutOfTheDerivedOdometer() = runTest {
        val anchor = testReading(anchorDate, anchorKm)
        val trip1 = testTrip(id = "t1", startedAt = trip1Start, endedAt = trip1End, distanceMeters = 50_000)
        val serviceLogs = FakeServiceLogRepository(readings = listOf(anchor))
        val trips = FakeTripRepository(initial = listOf(trip1))
        val observeDerived = ObserveDerivedOdometer(serviceLogs, trips)
        val reject = RejectTrip(trips)

        // 50 km counted at the default (unconfident) k=1.0 -> 10,050.
        val before = observeDerived(TEST_CAR, trip1.id).first()
        assertEquals(10_050, before?.current?.km)

        reject(trip1.id)

        // t1 no longer counts -> the drum drops back to the anchor.
        val after = observeDerived(TEST_CAR, trip1.id).first()
        assertEquals(anchorKm, after?.current?.km)
    }
}
