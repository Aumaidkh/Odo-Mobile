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
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
