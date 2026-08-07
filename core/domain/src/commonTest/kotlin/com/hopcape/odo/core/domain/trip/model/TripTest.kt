package com.hopcape.odo.core.domain.trip.model

import arrow.core.EitherNel
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class TripTest {

    private val id = TripId("trip-1")
    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val startedAt = Instant.parse("2026-08-07T08:00:00Z")
    private val endedAt = Instant.parse("2026-08-07T08:20:00Z")

    /** Valid baseline; each test overrides only the fields it exercises. */
    private fun create(
        startedAt: Instant = this.startedAt,
        endedAt: Instant = this.endedAt,
        distance: TripDistance = TripDistance.of(5_000),
        estimatedDistance: TripDistance = TripDistance.ZERO,
        mode: TripMode = TripMode.GPS_ONLY,
        status: TripStatus = TripStatus.RECORDED,
        startLat: Double? = 19.0760,
        startLon: Double? = 72.8777,
        endLat: Double? = 19.10,
        endLon: Double? = 72.90,
    ): EitherNel<DomainError, Trip> = Trip.create(
        id = id,
        carId = carId,
        ownerId = ownerId,
        startedAt = startedAt,
        endedAt = endedAt,
        distance = distance,
        estimatedDistance = estimatedDistance,
        mode = mode,
        status = status,
        startLat = startLat,
        startLon = startLon,
        endLat = endLat,
        endLon = endLon,
    )

    @Test
    fun validInput_buildsTrip() {
        val result = create()
        assertTrue(result.isRight(), "expected Right but was $result")
        val trip = result.getOrNull()!!
        assertEquals(5_000L, trip.distance.meters)
        assertEquals(19.0760, trip.startPoint?.lat)
        assertEquals(TripMode.GPS_ONLY, trip.mode)
    }

    @Test
    fun endBeforeStart_isRejected() {
        val earlier = Instant.parse("2026-08-07T07:00:00Z")
        val errors = create(endedAt = earlier).leftOrNull()
        assertTrue(errors != null && DomainError.InvalidTripWindow in errors)
    }

    @Test
    fun endEqualsStart_isRejected() {
        val errors = create(endedAt = startedAt).leftOrNull()
        assertTrue(errors != null && DomainError.InvalidTripWindow in errors)
    }

    @Test
    fun estimatedExceedsTotal_throws() {
        assertFailsWith<IllegalArgumentException> {
            create(distance = TripDistance.of(100), estimatedDistance = TripDistance.of(200))
        }
    }

    @Test
    fun missingCoordinatePair_leavesPointNull() {
        val trip = create(startLat = null, startLon = null).getOrNull()!!
        assertEquals(null, trip.startPoint)
    }

    @Test
    fun partialCoordinatePair_leavesPointNull() {
        val trip = create(startLat = 19.0, startLon = null).getOrNull()!!
        assertEquals(null, trip.startPoint)
    }

    @Test
    fun invalidStartCoordinate_isRejected() {
        val errors = create(startLat = 91.0).leftOrNull()
        assertTrue(errors != null && DomainError.InvalidCoordinate("lat", 91.0) in errors)
    }

    @Test
    fun multipleInvalidFields_accumulateAllErrors() {
        val errors = create(
            endedAt = startedAt, // invalid window
            startLat = 91.0, // invalid coordinate
        ).leftOrNull()
        assertTrue(errors != null, "expected Left with errors")
        assertTrue(DomainError.InvalidTripWindow in errors)
        assertTrue(DomainError.InvalidCoordinate("lat", 91.0) in errors)
        assertEquals(2, errors.size)
    }

    @Test
    fun withStatus_changesOnlyStatus() {
        val trip = create().getOrNull()!!
        val confirmed = trip.withStatus(TripStatus.CONFIRMED)
        assertEquals(TripStatus.CONFIRMED, confirmed.status)
        assertEquals(trip.distance, confirmed.distance)
        assertEquals(trip.id, confirmed.id)
    }

    @Test
    fun reconstitute_roundTripsFields() {
        val created = create().getOrNull()!!
        val restored = Trip.reconstitute(
            id = created.id,
            carId = created.carId,
            ownerId = created.ownerId,
            startedAt = created.startedAt,
            endedAt = created.endedAt,
            distanceMeters = created.distance.meters,
            estimatedMeters = created.estimatedDistance.meters,
            mode = created.mode,
            status = created.status,
            startLat = created.startPoint?.lat,
            startLon = created.startPoint?.lon,
            endLat = created.endPoint?.lat,
            endLon = created.endPoint?.lon,
        )
        assertEquals(created.distance.meters, restored.distance.meters)
        assertEquals(created.estimatedDistance.meters, restored.estimatedDistance.meters)
        assertEquals(created.startPoint, restored.startPoint)
        assertEquals(created.endPoint, restored.endPoint)
        assertEquals(created.mode, restored.mode)
        assertEquals(created.status, restored.status)
    }

    @Test
    fun reconstitute_withNoStoredPoints_leavesThemNull() {
        val restored = Trip.reconstitute(
            id = id,
            carId = carId,
            ownerId = ownerId,
            startedAt = startedAt,
            endedAt = endedAt,
            distanceMeters = 5_000,
            estimatedMeters = 0,
            mode = TripMode.BT_VERIFIED,
            status = TripStatus.RECORDED,
            startLat = null,
            startLon = null,
            endLat = null,
            endLon = null,
        )
        assertEquals(null, restored.startPoint)
        assertEquals(null, restored.endPoint)
    }
}
