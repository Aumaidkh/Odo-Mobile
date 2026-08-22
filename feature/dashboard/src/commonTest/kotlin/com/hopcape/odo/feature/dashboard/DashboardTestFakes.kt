package com.hopcape.odo.feature.dashboard

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceSource
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.refuel.DetectionApp
import com.hopcape.odo.core.domain.refuel.IgnoredMerchant
import com.hopcape.odo.core.domain.refuel.RefuelDetectionSettings
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared test doubles for the dashboard's use case and ViewModel. */

internal val TEST_OWNER = OwnerId("owner-1")
internal val TEST_CAR = CarId("car-1")

/** The day every fixture is dated against, so "expires in 19 days" means one thing. */
internal val TODAY = LocalDate(2026, 8, 1)
internal val NOW: Instant = Instant.parse("2026-08-01T09:00:00Z")

internal fun paise(value: Long): Amount = Amount.of(value).getOrElse { error("test paise=$value") }

internal fun km(value: Int): Distance = Distance.of(value).getOrElse { error("test km=$value") }

/** A clock that never moves, so a snapshot is the same on every emission. */
internal class FixedClock(private val now: Instant = NOW) : Clock {
    override fun now(): Instant = now
}

internal fun testCar(
    odometerKm: Int = 54_000,
    addedOn: LocalDate? = LocalDate(2026, 1, 5),
): Car = Car.reconstitute(
    id = TEST_CAR,
    ownerId = TEST_OWNER,
    make = "Maruti Suzuki",
    model = "Swift",
    variant = "VXI",
    year = 2020,
    fuelType = FuelType.PETROL,
    registrationNumber = "MH12AB1234",
    odometerKm = odometerKm,
    purchaseYear = 2021,
    nickname = null,
    isPrimary = true,
    addedOn = addedOn,
)

internal fun testProfile(name: String? = "Rahul", city: String? = "Pune"): OwnerProfile =
    OwnerProfile.reconstitute(
        id = TEST_OWNER,
        name = name,
        goal = null,
        onboardingCompletedAt = NOW,
        city = city,
    )

internal fun overcharged(by: Long): FairnessSnapshot = FairnessSnapshot(
    report = FairnessReport(
        city = "Pune",
        items = listOf(
            FairnessReportItem(
                label = "Full service",
                category = ServiceCategory.GENERAL_SERVICE,
                amount = paise(500_000),
                estimate = FairnessEstimate(
                    category = ServiceCategory.GENERAL_SERVICE,
                    city = "Pune",
                    cityAverage = paise(500_000 - by),
                    sampleSize = 20,
                ),
                verdict = FairnessVerdict.Over(paise(by)),
            ),
        ),
    ),
    checkedAt = NOW,
)

internal fun testEntry(
    id: String,
    date: LocalDate,
    odometerKm: Int = 50_000,
    paise: Long = 300_000,
    verified: Boolean = false,
    fairness: FairnessSnapshot? = null,
): ServiceLogEntry = ServiceLogEntry.reconstitute(
    id = ServiceLogId(id),
    carId = TEST_CAR,
    ownerId = TEST_OWNER,
    serviceDate = date,
    odometerKm = odometerKm,
    totalAmountPaise = paise,
    workshopName = "Sharma Motors",
    notes = "Oil change + filter",
    source = LogSource.MANUAL,
    billId = if (verified) BillId("bill-$id") else null,
    fairness = fairness,
)

internal fun testDocument(
    type: DocumentType = DocumentType.INSURANCE,
    expiresOn: LocalDate? = LocalDate(2027, 3, 31),
    addedOn: LocalDate? = LocalDate(2026, 6, 1),
    id: String = "doc-$type-$expiresOn",
): Document = Document.reconstitute(
    id = DocumentId(id),
    ownerId = TEST_OWNER,
    carId = TEST_CAR,
    type = type,
    storagePath = "documents/${TEST_CAR.value}/$id.pdf",
    source = DocumentSource.UPLOADED,
    addedOn = addedOn,
    expiresOn = expiresOn,
)

internal fun testReading(date: LocalDate, odometerKm: Int): OdometerReading =
    OdometerReading(logId = null, date = date, odometer = km(odometerKm))

/** A snapshot totalling [total], poured into the factors so none of them clamps. */
internal fun testSnapshot(
    id: String,
    at: String,
    total: Int,
    algoVersion: String = HealthScoreCalculator.RULES_VERSION,
): HealthSnapshot {
    var left = total
    val factors = HealthFactorKind.entries.map { kind ->
        val earned = minOf(left, kind.weight)
        left -= earned
        HealthFactor.of(kind, earned)
    }
    return HealthSnapshot(
        id = HealthSnapshotId(id),
        carId = TEST_CAR,
        ownerId = TEST_OWNER,
        score = HealthScore(factors = factors),
        computedAt = Instant.parse(at),
        algoVersion = algoVersion,
    )
}

internal class FakeCarRepository(car: Car? = testCar()) : CarRepository {
    private val stored = MutableStateFlow(car)

    override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
    override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
    override fun observePrimaryCar(): Flow<Car?> = stored
    override fun observe(id: CarId): Flow<Car?> = stored
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()

    fun emit(car: Car?) {
        stored.value = car
    }
}

internal class FakeServiceLogRepository(
    entries: List<ServiceLogEntry> = emptyList(),
    readings: List<OdometerReading> = emptyList(),
) : ServiceLogRepository {
    private val storedEntries = MutableStateFlow(entries)
    private val storedReadings = MutableStateFlow(readings)

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = storedEntries
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = storedReadings.value
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = storedReadings

    fun emit(entries: List<ServiceLogEntry>) {
        storedEntries.value = entries
    }

    /** The readings this fake currently holds, for a test that wants to derive an aggregate from them. */
    val readingsSnapshot: List<OdometerReading> get() = storedReadings.value
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

internal class FakeDocumentRepository(documents: List<Document> = emptyList()) : DocumentRepository {
    private val stored = MutableStateFlow(documents)

    override fun observe(carId: CarId): Flow<List<Document>> = stored
    override fun observe(id: DocumentId): Flow<Document?> = flowOf(null)
    override suspend fun add(document: Document): Either<DomainError, Document> = document.right()
    override suspend fun update(document: Document): Either<DomainError, Document> = document.right()
    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> = Unit.right()
    override suspend fun countForOwner(ownerId: OwnerId): Int = stored.value.size

    fun emit(documents: List<Document>) {
        stored.value = documents
    }
}

/** The car's fills, newest first, as Home's recent row reads them. */
/**
 * Detection settings, for the Home card that offers automatic logging.
 *
 * Defaults to detection already on, so the offer card is absent unless a test asks for it —
 * every existing assertion about Home's contents was written before the card existed.
 */
internal class FakeRefuelDetectionStore(
    private val settings: RefuelDetectionSettings = RefuelDetectionSettings(detectEnabled = true),
) : RefuelDetectionStore {
    override fun observeSettings(): Flow<RefuelDetectionSettings> = flowOf(settings)
    override suspend fun settings(): RefuelDetectionSettings = settings
    override suspend fun saveSettings(settings: RefuelDetectionSettings) = Unit
    override fun observeApps(): Flow<List<DetectionApp>> = flowOf(emptyList())
    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) = Unit
    override suspend fun registerApp(packageName: String, enabledByDefault: Boolean) = Unit
    override fun observeIgnoredMerchants(): Flow<List<IgnoredMerchant>> = flowOf(emptyList())
    override suspend fun ignoredMerchantKeys(): Set<String> = emptySet()
    override suspend fun ignoreMerchant(merchant: String) = Unit
    override suspend fun unignoreMerchant(merchantKey: String) = Unit
}

internal class FakeFuelFillRepository(fills: List<FuelFill> = emptyList()) : FuelFillRepository {
    private val stored = MutableStateFlow(fills)

    override suspend fun add(fill: FuelFill): Either<DomainError, FuelFill> = fill.right()
    override fun observeForCar(carId: CarId): Flow<List<FuelFill>> = stored
    override suspend fun latestForCar(carId: CarId): Either<DomainError, FuelFill?> =
        stored.value.firstOrNull().right()

    fun emit(fills: List<FuelFill>) {
        stored.value = fills
    }
}

internal class FakeHealthScoreRepository(history: List<HealthSnapshot> = emptyList()) : HealthScoreRepository {
    private val stored = MutableStateFlow(history)

    override suspend fun latest(carId: CarId): HealthSnapshot? = stored.value.maxByOrNull { it.computedAt }
    override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? =
        stored.value.filter { it.computedAt <= instant }.maxByOrNull { it.computedAt }

    override fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>> = stored
    override suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot> = snapshot.right()

    fun emit(history: List<HealthSnapshot>) {
        stored.value = history
    }
}

internal class FakeOwnerProfileRepository(profile: OwnerProfile? = testProfile()) : OwnerProfileRepository {
    private val stored = MutableStateFlow(profile)

    override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> = profile.right()
    override fun observe(): Flow<OwnerProfile?> = stored
    override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit> =
        Unit.right()

    override suspend fun delete(): Either<DomainError, Unit> = Unit.right().also { stored.value = null }

    fun emit(profile: OwnerProfile?) {
        stored.value = profile
    }
}

/** No price at all by default — the maintenance-only case Home has to survive. */
internal class FakeFuelPriceProvider(private val price: FuelPrice? = null) : FuelPriceProvider {
    override suspend fun priceFor(city: String?, fuelType: FuelType): FuelPrice? = price
    override fun priceChanges(): Flow<Unit> = flowOf(Unit)
}

internal fun testFuelPrice(paisePerLitre: Long = 10_450): FuelPrice = FuelPrice(
    city = "Pune",
    fuelType = FuelType.PETROL,
    pricePerUnit = paise(paisePerLitre),
    effectiveDate = TODAY,
    source = FuelPriceSource.SEED,
)

internal class FakeCurrentCityProvider(private val city: String? = "Pune") : CurrentCityProvider {
    override suspend fun currentCity(): String? = city
}

/** The active car, switchable so a test can watch Home follow it. */
internal class FakeActiveCarProvider(carId: CarId? = TEST_CAR) : ActiveCarProvider {
    private val stored = MutableStateFlow(carId)
    override val activeCarId: StateFlow<CarId?> = stored

    fun emit(carId: CarId?) {
        stored.value = carId
    }
}
