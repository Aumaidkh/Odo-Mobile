package com.hopcape.odo.feature.timeline

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Shared test doubles for the timeline's use case and ViewModels. */

internal val TEST_OWNER = OwnerId("owner-1")
internal val TEST_CAR = CarId("car-1")

internal fun testCar(
    addedOn: LocalDate? = LocalDate(2026, 1, 5),
    nickname: String? = "Swift VXI",
): Car = Car.reconstitute(
    id = TEST_CAR,
    ownerId = TEST_OWNER,
    make = "Maruti Suzuki",
    model = "Swift",
    variant = "VXI",
    year = 2020,
    fuelType = FuelType.PETROL,
    registrationNumber = "MH12AB1234",
    odometerKm = 54_000,
    purchaseYear = 2021,
    nickname = nickname,
    isPrimary = true,
    addedOn = addedOn,
)

internal fun testEntry(
    id: String,
    date: LocalDate,
    odometerKm: Int = 50_000,
    paise: Long = 300_000,
    verified: Boolean = false,
    notes: String? = "Oil change + filter",
): ServiceLogEntry = ServiceLogEntry.reconstitute(
    id = ServiceLogId(id),
    carId = TEST_CAR,
    ownerId = TEST_OWNER,
    serviceDate = date,
    odometerKm = odometerKm,
    totalAmountPaise = paise,
    workshopName = "Sharma Motors",
    notes = notes,
    source = LogSource.MANUAL,
    billId = if (verified) BillId("bill-$id") else null,
)

internal fun testDocument(
    type: DocumentType = DocumentType.INSURANCE,
    addedOn: LocalDate? = LocalDate(2026, 6, 1),
    expiresOn: LocalDate? = LocalDate(2027, 3, 31),
    id: String = "doc-$type-$addedOn",
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

/** The car's fills, newest first, as the timeline reads them. */
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

/** A stored fill, with only the fields the feed reads worth naming. */
internal fun testFill(
    id: String = "fill-1",
    date: LocalDate = LocalDate(2026, 7, 20),
    quantityMilli: Long = 21_110,
    amountPaise: Long = 200_000,
    odometerKm: Int = 34_612,
    station: String? = "Bharat Petroleum, Karol Bagh",
): FuelFill = FuelFill.reconstitute(
    id = FuelFillId(id),
    carId = TEST_CAR,
    ownerId = OwnerId("owner-1"),
    filledOn = date,
    odometerKm = odometerKm,
    quantityMilli = quantityMilli,
    unit = FuelUnit.LITRE,
    amountPaise = amountPaise,
    stationName = station,
    transactionRef = null,
)

/** Emits whatever it was given, and lets a test push a change mid-collection. */
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

internal class FakeServiceLogRepository(entries: List<ServiceLogEntry> = emptyList()) : ServiceLogRepository {
    private val stored = MutableStateFlow(entries)

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = stored
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = emptyList()
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(emptyList())

    fun emit(entries: List<ServiceLogEntry>) {
        stored.value = entries
    }
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

/** The active car, switchable so a test can watch the feed follow it. */
internal class FakeActiveCarProvider(carId: CarId? = TEST_CAR) : ActiveCarProvider {
    private val stored = MutableStateFlow(carId)
    override val activeCarId: StateFlow<CarId?> = stored

    fun emit(carId: CarId?) {
        stored.value = carId
    }
}
