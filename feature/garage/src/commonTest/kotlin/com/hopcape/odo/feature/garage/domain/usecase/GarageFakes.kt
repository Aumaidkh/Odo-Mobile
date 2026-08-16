package com.hopcape.odo.feature.garage.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared test doubles for the garage's use cases. */

internal val TEST_OWNER = OwnerId("owner-1")
internal val TEST_CAR = CarId("car-1")

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal fun testCar(
    id: CarId = TEST_CAR,
    odometerKm: Int = 45_000,
    nickname: String? = null,
    isPrimary: Boolean = true,
): Car = Car.reconstitute(
    id = id,
    ownerId = TEST_OWNER,
    make = "Maruti Suzuki",
    model = "Swift",
    variant = "VXI",
    year = 2020,
    fuelType = FuelType.PETROL,
    registrationNumber = "MH12AB1234",
    odometerKm = odometerKm,
    purchaseYear = 2021,
    nickname = nickname,
    isPrimary = isPrimary,
    addedOn = LocalDate(2026, 1, 5),
)

/** Records what was written, and can be told to fail the way a full disk would. */
internal class FakeCarRepository(
    car: Car? = null,
    private val failing: Boolean = false,
) : CarRepository {
    val stored = MutableStateFlow(car)
    val added = mutableListOf<Car>()
    val updated = mutableListOf<Car>()
    val deleted = mutableListOf<CarId>()

    override suspend fun add(car: Car): Either<DomainError, Car> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            added += car
            stored.value = car
            car.right()
        }

    override suspend fun update(car: Car): Either<DomainError, Car> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            updated += car
            stored.value = car
            car.right()
        }

    override fun observePrimaryCar(): Flow<Car?> = stored

    override fun observe(id: CarId): Flow<Car?> = stored

    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            deleted += id
            stored.value = null
            Unit.right()
        }
}

/** Only the odometer timeline matters to the garage, so the rest answers emptily. */
internal class FakeServiceLogRepository(
    private val readings: List<OdometerReading>? = emptyList(),
) : ServiceLogRepository {
    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? = readings
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(readings.orEmpty())
}

/** Emits a fixed [current] value for every car — a trip-aware "current odometer" double. */
internal class FakeCurrentOdometerProvider(private val current: Distance?) : CurrentOdometerProvider {
    override fun observeCurrent(carId: CarId): Flow<Distance?> = flowOf(current)
}

/**
 * A [CurrentOdometerProvider] over [logs]'s own readings, with no trips ever counted —
 * mirrors the pre-trip-aware behaviour so a test that does not care about trips can keep
 * asserting against [logs]'s latest reading.
 */
internal fun currentOdometerFrom(logs: FakeServiceLogRepository): CurrentOdometerProvider =
    object : CurrentOdometerProvider {
        override fun observeCurrent(carId: CarId): Flow<Distance?> =
            logs.observeOdometerReadings(carId).map { it.currentReading()?.odometer }
    }

internal class FakeVehicleCatalog(
    private val makes: List<String> = listOf("Maruti Suzuki", "Hyundai", "Tata"),
    private val models: Map<String, List<CarModel>> = mapOf(
        "Maruti Suzuki" to listOf(CarModel("Swift"), CarModel("Swift", "VXI")),
    ),
) : VehicleCatalog {
    val modelLookups = mutableListOf<String>()

    override suspend fun makes(): List<String> = makes
    override suspend fun popularMakes(): List<String> = makes.take(2)
    override suspend fun models(make: String): List<CarModel> {
        modelLookups += make
        return models[make].orEmpty()
    }
    override fun years(): List<Int> = listOf(2026, 2025, 2024)
    override fun fuelTypes(): List<FuelType> = FuelType.entries
}

/** Answers with [result] for any plate, and records what reached it. */
internal class FakeVehicleRegistryLookup(
    private val result: Either<DomainError, RegisteredVehicle> = DomainError.LookupUnavailable.left(),
) : VehicleRegistryLookup {
    val plates = mutableListOf<String>()

    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> {
        plates += registrationNumber.value
        return result
    }
}

internal class FixedIdGenerator(private val id: String = "new-car") : IdGenerator {
    override fun newId(): String = id
}

internal fun ownerProvider(ownerId: OwnerId = TEST_OWNER) = CurrentOwnerProvider { ownerId }

/*
 * :core:triptracker / trip-domain doubles for ObserveAutoOdometerCardState (F9). Garage's
 * own doubles, built against the same shared ports auto-odometer's tests use — not reused
 * from that feature, which garage never imports.
 */

internal fun testTrip(
    id: String,
    carId: CarId = TEST_CAR,
    startedAt: Instant,
    endedAt: Instant,
    distanceMeters: Long,
    status: TripStatus = TripStatus.RECORDED,
): Trip = Trip.reconstitute(
    id = TripId(id),
    carId = carId,
    ownerId = TEST_OWNER,
    startedAt = startedAt,
    endedAt = endedAt,
    distanceMeters = distanceMeters,
    estimatedMeters = 0,
    mode = TripMode.BT_VERIFIED,
    status = status,
    startLat = null,
    startLon = null,
    endLat = null,
    endLon = null,
)

/** Ignores [CarId] filtering — the app is single-car (plan decision), same as every other fake here. */
internal class FakeTripRepository(initial: List<Trip> = emptyList()) : TripRepository {
    val trips = MutableStateFlow(initial)

    override suspend fun add(trip: Trip): Either<DomainError, Trip> = trip.right()
    override suspend fun addWithParked(
        trip: Trip,
        gap: Trip?,
        parked: ParkedLocation,
    ): Either<DomainError, Trip> = trip.right()
    override fun observe(carId: CarId): Flow<List<Trip>> = trips
    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = trips
    override suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit> = Unit.right()
    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> =
        trips.value.filter { it.status.counted && it.startedAt > after }
    override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> =
        trips.value.filter { it.status.counted && it.startedAt >= from && it.startedAt <= to }
    override suspend fun parkedLocation(carId: CarId): ParkedLocation? = null
    override suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit> = Unit.right()

    /** "Keep trip routes" turning off. Nothing in the garage reaches for it. */
    override suspend fun forgetRoutes(): Either<DomainError, Unit> = Unit.right()
}

internal class FakeVehicleBondStore(initial: VehicleBond? = null) : VehicleBondStore {
    var current: VehicleBond? = initial

    override suspend fun bond(): VehicleBond? = current
    override suspend fun saveBond(bond: VehicleBond) {
        current = bond
    }
    override suspend fun clearBond() {
        current = null
    }
}

internal class FakeTripTracker(enabled: Boolean = false) : TripTracker {
    private val enabledFlow = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean) {
        enabledFlow.value = enabled
    }
    override suspend fun armFromPersistedState() = Unit
    override val isEnabled: Flow<Boolean> get() = enabledFlow
    override val status: Flow<TrackingStatus> get() = flowOf(TrackingStatus.Disabled)
    override suspend fun pauseActiveTrip() = Unit
    override suspend fun resumeActiveTrip() = Unit
    override suspend fun discardActiveTrip() = Unit
    override suspend fun startIfConnected() = Unit
}
