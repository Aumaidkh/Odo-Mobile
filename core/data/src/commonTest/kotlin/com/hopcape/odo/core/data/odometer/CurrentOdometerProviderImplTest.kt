package com.hopcape.odo.core.data.odometer

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CurrentOdometerProviderImplTest {

    private val carId = CarId("car-1")

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("test km=$value") }

    private fun reading(date: LocalDate, km: Int): OdometerReading =
        OdometerReading(logId = null, date = date, odometer = km(km))

    private fun trip(id: String, day: Int, km: Double, status: TripStatus = TripStatus.RECORDED): Trip = Trip.create(
        id = TripId(id),
        carId = carId,
        ownerId = OwnerId("owner-1"),
        startedAt = Instant.parse("2026-01-${day.toString().padStart(2, '0')}T08:00:00Z"),
        endedAt = Instant.parse("2026-01-${day.toString().padStart(2, '0')}T09:00:00Z"),
        distance = TripDistance.of((km * 1000).toLong()),
        estimatedDistance = TripDistance.ZERO,
        mode = TripMode.GPS_ONLY,
        status = status,
    ).getOrNull()!!

    /** Only what [CurrentOdometerProviderImpl] reads. */
    private class FakeServiceLogRepository(
        readings: List<OdometerReading> = emptyList(),
    ) : ServiceLogRepository {
        private val stored = MutableStateFlow(readings)
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = throw NotImplementedError()
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = throw NotImplementedError()
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
            throw NotImplementedError()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
            throw NotImplementedError()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = throw NotImplementedError()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = stored.value
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = stored

        fun emit(readings: List<OdometerReading>) {
            stored.value = readings
        }
    }

    /** Only what [CurrentOdometerProviderImpl] reads. */
    private class FakeTripRepository(trips: List<Trip> = emptyList()) : TripRepository {
        private val stored = MutableStateFlow(trips)
        override suspend fun add(trip: Trip): Either<DomainError, Trip> = trip.right()
        override suspend fun addWithParked(
            trip: Trip,
            gap: Trip?,
            parked: ParkedLocation,
        ): Either<DomainError, Trip> = trip.right()
        override fun observe(carId: CarId): Flow<List<Trip>> = stored
        override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = stored
        override suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit> =
            throw NotImplementedError()
        override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> = throw NotImplementedError()
        override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> =
            throw NotImplementedError()
        override suspend fun parkedLocation(carId: CarId): ParkedLocation? = null
        override suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit> = throw NotImplementedError()
        override suspend fun forgetRoutes(): Either<DomainError, Unit> = throw NotImplementedError()

        fun emit(trips: List<Trip>) {
            stored.value = trips
        }
    }

    @Test
    fun withNoReadings_emitsNull() = runTest {
        val provider = CurrentOdometerProviderImpl(FakeServiceLogRepository(), FakeTripRepository())

        assertNull(provider.observeCurrent(carId).first())
    }

    @Test
    fun combinesTheAnchorAndTheTripsIntoOneAggregate() = runTest {
        val provider = CurrentOdometerProviderImpl(
            FakeServiceLogRepository(listOf(reading(LocalDate(2026, 1, 1), 500))),
            FakeTripRepository(listOf(trip("t1", day = 2, km = 5.0))),
        )

        assertEquals(505, provider.observeCurrent(carId).first()?.km)
    }

    @Test
    fun aNewTripArriving_movesTheAggregate() = runTest {
        val logs = FakeServiceLogRepository(listOf(reading(LocalDate(2026, 1, 1), 500)))
        val trips = FakeTripRepository()
        val provider = CurrentOdometerProviderImpl(logs, trips)

        assertEquals(500, provider.observeCurrent(carId).first()?.km)

        trips.emit(listOf(trip("t1", day = 2, km = 5.0)))
        assertEquals(505, provider.observeCurrent(carId).first()?.km)
    }

    @Test
    fun aNewManualReading_reAnchorsAndDropsTheEarlierTrip() = runTest {
        val logs = FakeServiceLogRepository(listOf(reading(LocalDate(2026, 1, 1), 500)))
        val trips = FakeTripRepository(listOf(trip("t1", day = 2, km = 5.0)))
        val provider = CurrentOdometerProviderImpl(logs, trips)

        assertEquals(505, provider.observeCurrent(carId).first()?.km)

        // A new manual log at 510 km on day 3 absorbs the day-2 trip; nothing is left to add.
        logs.emit(listOf(reading(LocalDate(2026, 1, 1), 500), reading(LocalDate(2026, 1, 3), 510)))
        assertEquals(510, provider.observeCurrent(carId).first()?.km)
    }
}
