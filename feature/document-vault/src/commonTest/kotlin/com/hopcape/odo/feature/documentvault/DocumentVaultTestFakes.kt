package com.hopcape.odo.feature.documentvault

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.performance.api.APM
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId as CarIdAlias
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import kotlinx.coroutines.flow.Flow
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

internal val TEST_CAR = CarId("car-1")
internal val TEST_OWNER = OwnerId("owner-1")

/** 28 Jul 2026 — the day the vault tests resolve their documents against. */
internal val TEST_TODAY = LocalDate(2026, 7, 28)
internal val TEST_CLOCK = FixedClock(Instant.parse("2026-07-28T09:00:00Z"))

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** Hands out one known id, so a test can predict the storage key a new document gets. */
internal class FixedIdGenerator(private val id: String = "doc-new") : IdGenerator {
    override fun newId(): String = id
}

/** A stored document, built the way the data layer will rehydrate one. */
internal fun document(
    id: String,
    type: DocumentType,
    expiresOn: LocalDate?,
    issuedOn: LocalDate? = null,
    source: DocumentSource = DocumentSource.UPLOADED,
    carId: CarId = TEST_CAR,
    storagePath: String = "documents/${carId.value}/$id.pdf",
): Document = Document.reconstitute(
    id = DocumentId(id),
    ownerId = TEST_OWNER,
    carId = carId,
    type = type,
    storagePath = storagePath,
    source = source,
    issuedOn = issuedOn,
    expiresOn = expiresOn,
)

/**
 * In-memory [DocumentRepository], backed by a [MutableStateFlow] so readers re-emit after a
 * write. That matches how the real SQLDelight repository behaves.
 */
internal class FakeDocumentRepository(
    initial: List<Document> = emptyList(),
    private val failWith: DomainError? = null,
) : DocumentRepository {

    private val stored = MutableStateFlow(initial)

    override fun observe(carId: CarId): Flow<List<Document>> =
        stored.map { all -> all.filter { it.carId == carId } }

    override fun observe(id: DocumentId): Flow<Document?> =
        stored.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun add(document: Document): Either<DomainError, Document> {
        failWith?.let { return it.left() }
        stored.value = stored.value + document
        return document.right()
    }

    override suspend fun update(document: Document): Either<DomainError, Document> {
        failWith?.let { return it.left() }
        stored.value = stored.value.map { if (it.id == document.id) document else it }
        return document.right()
    }

    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> {
        failWith?.let { return it.left() }
        stored.value = stored.value.filterNot { it.id == id }
        return Unit.right()
    }

    override suspend fun countForOwner(ownerId: OwnerId): Int =
        stored.value.count { it.ownerId == ownerId }
}

/**
 * In-memory [DocumentFileStore]. Records every save and delete so a test can check that the
 * file and the row are written together, or that neither is when [failWith] is set.
 */
internal class FakeDocumentFileStore(
    private val failWith: DomainError? = null,
    stored: Set<String> = emptySet(),
) : DocumentFileStore {

    val saved = stored.toMutableSet()
    val deleted = mutableListOf<String>()

    override suspend fun save(
        pickedRef: String,
        carId: CarId,
        documentId: DocumentId,
    ): Either<DomainError, String> {
        failWith?.let { return it.left() }
        val key = "documents/${carId.value}/${documentId.value}.pdf"
        saved += key
        return key.right()
    }

    override suspend fun delete(storagePath: String) {
        saved -= storagePath
        deleted += storagePath
    }

    override suspend fun exists(storagePath: String): Boolean = storagePath in saved
}

/** Records what the feature tracked, so a test can assert on the event names it ships. */
internal class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()

    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }

    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit

    fun names(): List<String> = events.map { it.first }
}

/**
 * The real telemetry facade over a recording tracker.
 *
 * The logger and tracer are the app's own facades, which stay inert until the bootstrap
 * configures them — so a test exercises the same code the app runs, without a fake in the
 * middle that could drift from it.
 */
internal fun testTelemetry(analytics: RecordingAnalytics = RecordingAnalytics()) =
    DocumentVaultTelemetry(
        logger = HLogger.asLogger(),
        analytics = analytics,
        tracer = APM.asTracer(),
        ids = FixedIdGenerator("trace"),
    )

/** The active car, or none before setup has stored one. */
internal class FakeActiveCarProvider(carId: CarIdAlias? = TEST_CAR) : ActiveCarProvider {
    private val state = MutableStateFlow(carId)
    override val activeCarId: StateFlow<CarIdAlias?> = state
}

internal val TEST_OWNER_PROVIDER = CurrentOwnerProvider { TEST_OWNER }
