package com.hopcape.odo.feature.costtracker.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceOverrides
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared test doubles for the cost tracker's use cases. */

internal val TEST_OWNER = OwnerId("owner-1")
internal val TEST_CAR = CarId("car-1")

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal fun testCar(fuelType: FuelType = FuelType.PETROL): Car = Car.reconstitute(
    id = TEST_CAR,
    ownerId = TEST_OWNER,
    make = "Maruti Suzuki",
    model = "Swift",
    variant = "VXI",
    year = 2020,
    fuelType = fuelType,
    registrationNumber = "MH12AB1234",
    odometerKm = 45_000,
    purchaseYear = 2021,
    nickname = null,
    isPrimary = true,
    addedOn = LocalDate(2026, 1, 5),
)

internal fun testEntry(
    id: String,
    date: LocalDate,
    odometerKm: Int,
    paise: Long,
    categories: Set<ServiceCategory> = emptySet(),
): ServiceLogEntry = ServiceLogEntry.reconstitute(
    id = ServiceLogId(id),
    carId = TEST_CAR,
    ownerId = TEST_OWNER,
    serviceDate = date,
    odometerKm = odometerKm,
    totalAmountPaise = paise,
    workshopName = null,
    notes = null,
    source = LogSource.MANUAL,
    billId = null,
    categories = categories,
)

internal fun reading(date: LocalDate, km: Int) =
    OdometerReading(logId = null, date = date, odometer = Distance.of(km).getOrNull()!!)

internal fun paise(value: Long): Amount = Amount.of(value).getOrNull()!!

/** Only reads matter here; the writes answer as a working repository would. */
internal class FakeCarRepository(car: Car?) : CarRepository {
    private val stored = MutableStateFlow(car)

    override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
    override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
    override suspend fun findByRegistration(ownerId: OwnerId, registrationNumber: RegistrationNumber): Car? = null
    override fun observePrimaryCar(): Flow<Car?> = stored
    override fun observe(id: CarId): Flow<Car?> = stored
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()
}

internal class FakeServiceLogRepository(
    private val entries: List<ServiceLogEntry> = emptyList(),
    private val readings: List<OdometerReading> = emptyList(),
) : ServiceLogRepository {
    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(entries)
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = readings
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(readings)
}

/**
 * Answers with whatever [price] holds, records what was asked for, and re-emits when a test
 * changes it — the way the real adapter re-emits when the owner sets a rate.
 */
internal class FakeFuelPriceProvider(
    price: FuelPrice? = null,
) : FuelPriceProvider {
    val lookups = mutableListOf<Pair<String?, FuelType>>()
    private val changes = MutableStateFlow(price)

    override suspend fun priceFor(city: String?, fuelType: FuelType): FuelPrice? {
        lookups += city to fuelType
        return changes.value
    }

    override fun priceChanges(): Flow<Unit> = changes.map { }

    /** Stand in for the owner setting or clearing their own rate. */
    fun set(price: FuelPrice?) {
        changes.value = price
    }
}

/** In-memory override store, plus a switch for the failing-write case. */
internal class FakeFuelPriceOverrides(private val failing: Boolean = false) : FuelPriceOverrides {
    val set = mutableListOf<Triple<FuelType, Amount, LocalDate>>()
    val cleared = mutableListOf<FuelType>()

    override suspend fun setOverride(
        fuelType: FuelType,
        pricePerUnit: Amount,
        on: LocalDate,
    ): Either<DomainError, Unit> = if (failing) {
        DomainError.PersistenceFailure("disk full").left()
    } else {
        set += Triple(fuelType, pricePerUnit, on)
        Unit.right()
    }

    override suspend fun clearOverride(fuelType: FuelType): Either<DomainError, Unit> = if (failing) {
        DomainError.PersistenceFailure("disk full").left()
    } else {
        cleared += fuelType
        Unit.right()
    }
}

internal fun testFuelPrice(
    pricePaise: Long = 10_500,
    city: String? = "pune",
    fuelType: FuelType = FuelType.PETROL,
    source: FuelPriceSource = FuelPriceSource.SEED,
    on: LocalDate = LocalDate(2026, 8, 1),
) = FuelPrice(
    city = city,
    fuelType = fuelType,
    pricePerUnit = paise(pricePaise),
    effectiveDate = on,
    source = source,
)

internal fun cityProvider(city: String?) = CurrentCityProvider { city }

/** Settings that never change — the units the figures are shown in. */
internal class FakeSettingsRepository(
    private val settings: AppSettings = AppSettings.Default,
) : AppSettingsRepository {
    override fun observe(): Flow<AppSettings> = flowOf(settings)
    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> = settings.right()
}
