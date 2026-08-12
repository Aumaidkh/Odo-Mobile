package com.hopcape.odo.infrastructure.database.trip

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQL behaviour for [SqlDelightTripLocalDataSource] — round-trip mapping, `insertWithParked`
 * atomicity, `countedSince` status filtering, and sync-column stamping. Error mapping lives
 * in `TripRepositoryImplTest` instead, against a fake port.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightTripLocalDataSourceTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun local(db: OdoDatabase, now: String = "2026-08-07T10:00:00Z") =
        SqlDelightTripLocalDataSource(database = db, clock = FixedClock(Instant.parse(now)), dispatcher = Dispatchers.Unconfined)

    private fun trip(
        id: String = "trip-1",
        startedAt: String = "2026-08-07T08:00:00Z",
        endedAt: String = "2026-08-07T08:20:00Z",
        distanceM: Long = 5_000,
        estimatedM: Long = 0,
        mode: TripMode = TripMode.BT_VERIFIED,
        status: TripStatus = TripStatus.RECORDED,
        startLat: Double? = 19.0760,
        startLon: Double? = 72.8777,
        endLat: Double? = 19.10,
        endLon: Double? = 72.90,
    ): Trip = Trip.create(
        id = TripId(id),
        carId = carId,
        ownerId = ownerId,
        startedAt = Instant.parse(startedAt),
        endedAt = Instant.parse(endedAt),
        distance = TripDistance.of(distanceM),
        estimatedDistance = TripDistance.of(estimatedM),
        mode = mode,
        status = status,
        startLat = startLat,
        startLon = startLon,
        endLat = endLat,
        endLon = endLon,
    ).getOrNull()!!

    @Test
    fun insertedTrip_roundTripsEveryField() = runTest {
        val db = newDb()
        local(db).insert(trip())

        val stored = local(db).observeByCar(carId).first().single()
        assertEquals("trip-1", stored.id.value)
        assertEquals(carId, stored.carId)
        assertEquals(ownerId, stored.ownerId)
        assertEquals(5_000L, stored.distance.meters)
        assertEquals(TripMode.BT_VERIFIED, stored.mode)
        assertEquals(TripStatus.RECORDED, stored.status)
        assertEquals(19.0760, stored.startPoint?.lat)
        assertEquals(72.8777, stored.startPoint?.lon)
        assertEquals(19.10, stored.endPoint?.lat)
    }

    @Test
    fun writesLandPendingWithTheStampedTimestamp() = runTest {
        val db = newDb()
        local(db, now = "2026-08-07T10:00:00Z").insert(trip())

        val row = db.tripQueries.selectById("trip-1", ::tripFromRow).executeAsOne()
        // selectById returns a Trip via the mapper; read the raw sync columns via selectPending instead.
        val pending = db.tripQueries.selectPending().executeAsList()
        assertEquals(1, pending.size)
        assertEquals("2026-08-07T10:00:00Z", pending.single().updated_at)
        assertEquals(SyncStatus.PENDING.name, pending.single().sync_status)
        assertEquals("trip-1", row.id.value)
    }

    @Test
    fun insertWithParked_writesTheTripTheTwinAndTheParkedLocationTogether() = runTest {
        val db = newDb()
        val gap = trip(id = "trip-gap", mode = TripMode.GAP_INFERRED, status = TripStatus.NEEDS_CONFIRMATION)
        val parked = ParkedLocation(carId, point(19.20, 73.0), Instant.parse("2026-08-07T08:20:00Z"))

        local(db).insertWithParked(trip(), gap, parked)

        val all = local(db).observeByCar(carId).first()
        assertEquals(setOf("trip-1", "trip-gap"), all.map { it.id.value }.toSet())
        assertEquals(parked.point.lat, local(db).parkedLocation(carId)?.point?.lat)
    }

    @Test
    fun insertWithParked_aFailureInAnyWriteRollsBackAllThree() = runTest {
        val db = newDb()
        val duplicateIdGap = trip(id = "trip-1") // same id as the main trip -> PRIMARY KEY violation
        val parked = ParkedLocation(carId, point(19.20, 73.0), Instant.parse("2026-08-07T08:20:00Z"))

        assertFailsWith<Exception> {
            local(db).insertWithParked(trip(), duplicateIdGap, parked)
        }

        assertTrue(local(db).observeByCar(carId).first().isEmpty(), "the main trip must not survive a rolled-back transaction")
        assertNull(local(db).parkedLocation(carId), "the parked-location upsert must not survive either")
    }

    @Test
    fun countedSince_onlyReturnsRecordedAndConfirmedTripsAfterTheGivenInstant() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(trip(id = "before", startedAt = "2026-08-07T07:00:00Z", endedAt = "2026-08-07T07:10:00Z", status = TripStatus.RECORDED))
        local.insert(trip(id = "after-recorded", startedAt = "2026-08-07T09:00:00Z", endedAt = "2026-08-07T09:10:00Z", status = TripStatus.RECORDED))
        local.insert(trip(id = "after-confirmed", startedAt = "2026-08-07T09:30:00Z", endedAt = "2026-08-07T09:40:00Z", status = TripStatus.CONFIRMED))
        local.insert(trip(id = "after-needs-confirmation", startedAt = "2026-08-07T09:50:00Z", endedAt = "2026-08-07T10:00:00Z", status = TripStatus.NEEDS_CONFIRMATION))
        local.insert(trip(id = "after-rejected", startedAt = "2026-08-07T10:10:00Z", endedAt = "2026-08-07T10:20:00Z", status = TripStatus.REJECTED))

        val counted = local.countedSince(carId, Instant.parse("2026-08-07T08:00:00Z"))

        assertEquals(setOf("after-recorded", "after-confirmed"), counted.map { it.id.value }.toSet())
    }

    @Test
    fun countedBetween_bothBoundsInclusive_excludesOutsideValues() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(trip(id = "before", startedAt = "2026-08-07T07:00:00Z", endedAt = "2026-08-07T07:10:00Z"))
        local.insert(trip(id = "at-from", startedAt = "2026-08-07T08:00:00Z", endedAt = "2026-08-07T08:10:00Z"))
        local.insert(trip(id = "inside", startedAt = "2026-08-07T09:00:00Z", endedAt = "2026-08-07T09:10:00Z"))
        local.insert(trip(id = "at-to", startedAt = "2026-08-07T10:00:00Z", endedAt = "2026-08-07T10:10:00Z"))
        local.insert(trip(id = "after", startedAt = "2026-08-07T10:00:01Z", endedAt = "2026-08-07T10:10:02Z"))

        val counted = local.countedBetween(carId, Instant.parse("2026-08-07T08:00:00Z"), Instant.parse("2026-08-07T10:00:00Z"))

        assertEquals(setOf("at-from", "inside", "at-to"), counted.map { it.id.value }.toSet())
    }

    @Test
    fun countedBetween_onlyRecordedAndConfirmed() = runTest {
        val db = newDb()
        val local = local(db)
        local.insert(trip(id = "confirmed", startedAt = "2026-08-07T08:30:00Z", endedAt = "2026-08-07T08:40:00Z", status = TripStatus.CONFIRMED))
        local.insert(trip(id = "needs-confirmation", startedAt = "2026-08-07T08:31:00Z", endedAt = "2026-08-07T08:41:00Z", status = TripStatus.NEEDS_CONFIRMATION))
        local.insert(trip(id = "rejected", startedAt = "2026-08-07T08:32:00Z", endedAt = "2026-08-07T08:42:00Z", status = TripStatus.REJECTED))

        val counted = local.countedBetween(carId, Instant.parse("2026-08-07T08:00:00Z"), Instant.parse("2026-08-07T09:00:00Z"))

        assertEquals(setOf("confirmed"), counted.map { it.id.value }.toSet())
    }

    @Test
    fun deleteAllForCar_removesTripsParkedLocationAndSession_leavesOtherCarUntouched() = runTest {
        val db = newDb()
        val local = local(db)
        val otherCarId = CarId("car-2")

        local.insertWithParked(
            trip = trip(id = "trip-1"),
            gap = null,
            parked = ParkedLocation(carId, point(19.0760, 72.8777), Instant.parse("2026-08-07T08:20:00Z")),
        )
        db.tripSessionQueries.insertSession(
            phase = "TRACKING",
            carId = carId.value,
            mode = "BT_VERIFIED",
            startedAt = "2026-08-07T08:00:00Z",
            distanceM = 500,
            estimatedM = 0,
            lastFixLat = null,
            lastFixLon = null,
            lastFixAccuracyM = null,
            lastFixSpeedMps = null,
            lastFixAt = null,
            stitchDeadline = null,
        )

        val otherTrip = Trip.create(
            id = TripId("trip-other"),
            carId = otherCarId,
            ownerId = ownerId,
            startedAt = Instant.parse("2026-08-07T08:00:00Z"),
            endedAt = Instant.parse("2026-08-07T08:20:00Z"),
            distance = TripDistance.of(2_000),
            estimatedDistance = TripDistance.ZERO,
            mode = TripMode.BT_VERIFIED,
            status = TripStatus.RECORDED,
        ).getOrNull()!!
        local.insertWithParked(
            trip = otherTrip,
            gap = null,
            parked = ParkedLocation(otherCarId, point(19.20, 73.0), Instant.parse("2026-08-07T08:20:00Z")),
        )

        local.deleteAllForCar(carId)

        assertTrue(local.observeByCar(carId).first().isEmpty(), "car-1's trips must be gone")
        assertNull(local.parkedLocation(carId), "car-1's parked location must be gone")
        assertNull(db.tripSessionQueries.selectSession().executeAsOneOrNull(), "car-1's live session must be gone")

        assertEquals(1, local.observeByCar(otherCarId).first().size, "car-2's trip must survive")
        assertEquals(otherCarId, local.parkedLocation(otherCarId)?.carId, "car-2's parked location must survive")
    }

    @Test
    fun deleteAllForCar_leavesAnotherCarsLiveSessionAlone() = runTest {
        val db = newDb()
        val local = local(db)
        val otherCarId = CarId("car-2")
        db.tripSessionQueries.insertSession(
            phase = "TRACKING",
            carId = otherCarId.value,
            mode = "BT_VERIFIED",
            startedAt = "2026-08-07T08:00:00Z",
            distanceM = 500,
            estimatedM = 0,
            lastFixLat = null,
            lastFixLon = null,
            lastFixAccuracyM = null,
            lastFixSpeedMps = null,
            lastFixAt = null,
            stitchDeadline = null,
        )

        local.deleteAllForCar(carId)

        assertEquals(otherCarId.value, db.tripSessionQueries.selectSession().executeAsOneOrNull()?.car_id)
    }

    @Test
    fun updateStatus_missingTrip_answersFalse() = runTest {
        val db = newDb()
        assertTrue(!local(db).updateStatus(TripId("nope"), TripStatus.CONFIRMED))
    }

    @Test
    fun updateStatus_existingTrip_updatesAndReturnsPending() = runTest {
        val db = newDb()
        local(db).insert(trip(status = TripStatus.NEEDS_CONFIRMATION))

        val updated = local(db).updateStatus(TripId("trip-1"), TripStatus.CONFIRMED)

        assertTrue(updated)
        val stored = local(db).observeByCar(carId).first().single()
        assertEquals(TripStatus.CONFIRMED, stored.status)
    }

    private fun point(lat: Double, lon: Double): GeoPoint = GeoPoint.of(lat, lon).getOrNull()!!
}
