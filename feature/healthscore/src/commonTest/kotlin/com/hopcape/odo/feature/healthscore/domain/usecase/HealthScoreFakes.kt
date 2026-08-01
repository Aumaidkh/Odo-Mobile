package com.hopcape.odo.feature.healthscore.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
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
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared test doubles for the health-score use cases. */

internal val TEST_OWNER = OwnerId("owner-1")
internal val TEST_CAR = CarId("car-1")

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** Hands out predictable ids so a written snapshot can be identified. */
internal class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "snap-${next++}"
}

internal fun testEntry(
    id: String,
    date: LocalDate,
    odometerKm: Int,
    paise: Long = 300_000,
    verified: Boolean = false,
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
    billId = if (verified) BillId("bill-$id") else null,
)

internal fun testDocument(
    type: DocumentType = DocumentType.INSURANCE,
    expiresOn: LocalDate? = LocalDate(2027, 3, 31),
    id: String = "doc-$type",
): Document = Document.reconstitute(
    id = DocumentId(id),
    ownerId = TEST_OWNER,
    carId = TEST_CAR,
    type = type,
    storagePath = "documents/${TEST_CAR.value}/$id.pdf",
    source = DocumentSource.UPLOADED,
    addedOn = null,
    expiresOn = expiresOn,
)

internal fun reading(date: LocalDate, km: Int) =
    OdometerReading(logId = null, date = date, odometer = Distance.of(km).getOrNull()!!)

internal fun testScore(
    maintenance: Int = 28,
    documentation: Int = 24,
    cost: Int = 14,
    history: Int = 8,
) = HealthScore(
    factors = listOf(
        HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenance),
        HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentation),
        HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, cost),
        HealthFactor.of(HealthFactorKind.HISTORY, history),
    ),
)

internal fun testSnapshot(
    id: String = "snap-old",
    score: HealthScore = testScore(),
    computedAt: String = "2026-07-01T10:00:00Z",
) = HealthSnapshot(
    id = HealthSnapshotId(id),
    carId = TEST_CAR,
    ownerId = TEST_OWNER,
    score = score,
    computedAt = Instant.parse(computedAt),
)

/** Emits whatever it was given, and lets a test push a change mid-collection. */
internal class FakeServiceLogRepository(
    entries: List<ServiceLogEntry> = emptyList(),
    readings: List<OdometerReading> = emptyList(),
) : ServiceLogRepository {
    private val stored = MutableStateFlow(entries)
    private val storedReadings = MutableStateFlow(readings)

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = stored
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = storedReadings.value
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = storedReadings

    fun emit(entries: List<ServiceLogEntry>, readings: List<OdometerReading>) {
        stored.value = entries
        storedReadings.value = readings
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

/** In-memory score history, plus a switch for the failing-write case. */
internal class FakeHealthScoreRepository(
    private val history: MutableList<HealthSnapshot> = mutableListOf(),
    private val failing: Boolean = false,
) : HealthScoreRepository {
    val recorded = mutableListOf<HealthSnapshot>()
    val baselineAsks = mutableListOf<Instant>()

    override suspend fun latest(carId: CarId): HealthSnapshot? =
        history.filter { it.carId == carId }.maxByOrNull { it.computedAt }

    override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? {
        baselineAsks += instant
        return history.filter { it.carId == carId && it.computedAt <= instant }
            .maxByOrNull { it.computedAt }
    }

    override suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            recorded += snapshot
            history += snapshot
            snapshot.right()
        }
}
