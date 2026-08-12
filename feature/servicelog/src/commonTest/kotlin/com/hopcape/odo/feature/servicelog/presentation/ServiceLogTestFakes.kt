package com.hopcape.odo.feature.servicelog.presentation

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.servicelog.domain.usecase.ResolveEntryFairnessUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * In-memory [ServiceLogRepository]. [carBaselineKm] emulates the car's onboarding reading
 * (`null` = no such car), dated [carBaselineDate] so it takes its place on the timeline.
 */
internal class FakeServiceLogRepository(
    initial: List<ServiceLogEntry> = emptyList(),
    private val carBaselineKm: Int? = 0,
    private val carBaselineDate: LocalDate = LocalDate(2020, 1, 1),
) : ServiceLogRepository {

    val entries = MutableStateFlow(initial)
    var addCount = 0
    var deleteCount = 0

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> =
        entries.map { list -> list.filter { it.carId == carId }.sortedByDescending { it.serviceDate } }

    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> =
        entries.map { list -> list.find { it.id == id } }

    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
        addCount++
        entries.update { it + entry }
        return entry.right()
    }

    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
        entries.update { list -> list.map { if (it.id == entry.id) entry else it } }
        return entry.right()
    }

    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> {
        deleteCount++
        entries.update { list -> list.filterNot { it.id == id } }
        return Unit.right()
    }

    override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? {
        val baselineKm = carBaselineKm ?: return null
        val baseline = OdometerReading(
            logId = null,
            date = carBaselineDate,
            odometer = Distance.of(baselineKm).getOrElse { error("test baseline km=$baselineKm") },
        )
        val logs = entries.value
            .filter { it.carId == carId }
            .map { OdometerReading(logId = it.id, date = it.serviceDate, odometer = it.odometer) }
        return listOf(baseline) + logs
    }

    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> =
        entries.map { odometerReadings(carId).orEmpty() }
}

/**
 * In-memory [CarRepository] holding one car. Only the reads the record needs are real; the
 * writes answer successfully so a test never has to care about them.
 */
internal class FakeCarRepository(initial: Car? = null) : CarRepository {

    val car = MutableStateFlow(initial)

    override suspend fun add(car: Car): Either<DomainError, Car> = car.right().also { this.car.value = car }
    override suspend fun update(car: Car): Either<DomainError, Car> = car.right().also { this.car.value = car }
    override fun observePrimaryCar(): Flow<Car?> = car
    override fun observe(id: CarId): Flow<Car?> = car.map { held -> held?.takeIf { it.id == id } }
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()
}

/** In-memory [DocumentRepository] over a fixed list. */
internal class FakeDocumentRepository(initial: List<Document> = emptyList()) : DocumentRepository {

    val documents = MutableStateFlow(initial)

    override fun observe(carId: CarId): Flow<List<Document>> =
        documents.map { list -> list.filter { it.carId == carId } }

    override fun observe(id: DocumentId): Flow<Document?> =
        documents.map { list -> list.find { it.id == id } }

    override suspend fun add(document: Document): Either<DomainError, Document> =
        document.right().also { documents.update { it + document } }

    override suspend fun update(document: Document): Either<DomainError, Document> =
        document.right().also { list -> documents.update { held -> held.map { if (it.id == document.id) document else it } } }

    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> =
        Unit.right().also { documents.update { list -> list.filterNot { it.id == id } } }

    override suspend fun countForOwner(ownerId: OwnerId): Int = documents.value.count { it.ownerId == ownerId }
}

/** In-memory [HealthScoreRepository] over a fixed history, oldest first. */
internal class FakeHealthScoreRepository(initial: List<HealthSnapshot> = emptyList()) : HealthScoreRepository {

    val history = MutableStateFlow(initial)

    override suspend fun latest(carId: CarId): HealthSnapshot? =
        history.value.filter { it.carId == carId }.maxByOrNull { it.computedAt }

    override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? =
        history.value.filter { it.carId == carId && it.computedAt <= instant }.maxByOrNull { it.computedAt }

    override fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>> =
        history.map { list -> list.filter { it.carId == carId } }

    override suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot> =
        snapshot.right().also { history.update { it + snapshot } }
}

/** In-memory [OwnerProfileRepository] holding one profile. */
internal class FakeOwnerProfileRepository(initial: OwnerProfile? = null) : OwnerProfileRepository {

    val profile = MutableStateFlow(initial)

    override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> =
        profile.right().also { this.profile.value = profile }

    override fun observe(): Flow<OwnerProfile?> = profile

    override suspend fun delete(): Either<DomainError, Unit> = Unit.right().also { profile.value = null }
}

/** The car every record test prints — a 2020 Swift VXI on a Maharashtra plate. */
internal fun testCar(
    addedOn: LocalDate? = LocalDate(2020, 8, 6),
    purchaseYear: Int? = 2020,
    odometerKm: Int = 54_120,
    nickname: String? = null,
): Car = Car.reconstitute(
    id = TEST_CAR,
    ownerId = TEST_OWNER,
    make = "Maruti",
    model = "Swift",
    variant = "VXI",
    year = 2020,
    fuelType = FuelType.PETROL,
    registrationNumber = "MH12AB1234",
    odometerKm = odometerKm,
    purchaseYear = purchaseYear,
    nickname = nickname,
    isPrimary = true,
    addedOn = addedOn,
)

/** A stored profile with a name on it. */
internal fun testOwner(name: String? = "Rahul Deshmukh"): OwnerProfile = OwnerProfile.reconstitute(
    id = TEST_OWNER,
    name = name,
    goal = null,
    onboardingCompletedAt = null,
)

/** A filed paper of [type], valid until [expiresOn] unless that is null. */
internal fun testDocument(
    type: DocumentType,
    addedOn: LocalDate? = LocalDate(2026, 6, 1),
    expiresOn: LocalDate? = LocalDate(2027, 7, 3),
): Document = Document.reconstitute(
    id = DocumentId("doc-$type-$addedOn"),
    ownerId = TEST_OWNER,
    carId = TEST_CAR,
    type = type,
    storagePath = "documents/doc-$type.pdf",
    source = DocumentSource.UPLOADED,
    addedOn = addedOn,
    issuedOn = null,
    expiresOn = expiresOn,
)

/** A score snapshot adding up to [total], poured into the factors in weight order. */
internal fun testSnapshot(at: String, total: Int): HealthSnapshot {
    var left = total
    return HealthSnapshot(
        id = HealthSnapshotId("snap-$at"),
        carId = TEST_CAR,
        ownerId = TEST_OWNER,
        score = HealthScore(
            factors = HealthFactorKind.entries.map { kind ->
                val earned = minOf(left, kind.weight)
                left -= earned
                HealthFactor.of(kind, earned)
            },
        ),
        computedAt = Instant.parse(at),
        algoVersion = "rule-v1",
    )
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

internal object NoopLogger : Logger {
    override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) {}
    override fun flush() {}
}

internal object NoopAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) {}
    override fun track(eventName: String, properties: Map<String, Any?>) {}
    override fun setConsent(status: ConsentStatus) {}
    override fun flush() {}
}

internal class FixedIdGenerator(private val id: String = "log-new") : IdGenerator {
    override fun newId(): String = id
}

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** 2026-07-03 in UTC — the fixed "today" for date-guard tests. */
internal val TEST_CLOCK = FixedClock(Instant.parse("2026-07-03T10:00:00Z"))
internal val TEST_CAR = CarId("car-1")
internal val TEST_OWNER = OwnerId("owner-1")

internal fun testEntry(
    id: String,
    km: Int,
    paise: Long = 0,
    verified: Boolean = false,
    date: LocalDate = LocalDate(2026, 1, 1),
    workshop: String? = "Sharma Motors",
    notes: String? = null,
    categories: Set<ServiceCategory> = emptySet(),
    lineItems: List<String> = emptyList(),
): ServiceLogEntry = ServiceLogEntry.reconstitute(
    id = ServiceLogId(id),
    carId = TEST_CAR,
    ownerId = TEST_OWNER,
    serviceDate = date,
    odometerKm = km,
    totalAmountPaise = paise,
    workshopName = workshop,
    notes = notes,
    source = if (verified) LogSource.SCANNED else LogSource.MANUAL,
    billId = if (verified) BillId("bill-$id") else null,
    categories = categories,
    // Priced lines name the work exactly, which is how a scanned bill reaches the record.
    lineItems = lineItems.map { label ->
        ServiceLogLineItem(label = label, category = ServiceCategory.OTHER, amount = Amount.ZERO)
    },
)

/** The user's city for tests. */
internal val TEST_CITY = CurrentCityProvider { "Pune" }

/** Benchmarks keyed by category → (averagePaise, sampleSize); empty = no verdicts. */
internal class FakeFairnessRepository(
    private val table: Map<ServiceCategory, Pair<Long, Int>> = emptyMap(),
) : FairnessRepository {
    override suspend fun estimates(
        categories: Set<ServiceCategory>,
        city: String,
    ): Map<ServiceCategory, FairnessEstimate> = categories.mapNotNull { category ->
        table[category]?.let { (avg, sample) ->
            category to FairnessEstimate(category, city, Amount.of(avg).getOrElse { Amount.ZERO }, sample)
        }
    }.toMap()
}

internal class FakeOverchargeReportRepository : OverchargeReportRepository {
    var submitted: OverchargeReport? = null
    override suspend fun submit(report: OverchargeReport): Either<DomainError, Unit> {
        submitted = report
        return Unit.right()
    }
}

/** A [ResolveEntryFairnessUseCase] over an optional benchmark table. */
internal fun testResolveFairness(table: Map<ServiceCategory, Pair<Long, Int>> = emptyMap()) =
    ResolveEntryFairnessUseCase(FakeFairnessRepository(table))
