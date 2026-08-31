package com.hopcape.odo.feature.garage.presentation

import arrow.core.Either
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.garage.domain.usecase.FixedIdGenerator
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Test doubles for the garage's ViewModels. */

/** Records what was tracked, so a test can assert on the event a screen is meant to emit. */
internal class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

private object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit
    override fun flush() = Unit
}

private class FakeSpan(
    override val spanId: String,
    override val traceId: String,
    override val parentSpanId: String?,
    override val name: String,
) : Span {
    override fun setAttribute(key: String, value: Any?): Span = this
}

private object NoopTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        FakeSpan("span", traceId, parentSpanId, name)
    override fun endSpan(span: Span) = Unit
    override fun flush() = Unit
}

internal fun testTelemetry(analytics: AnalyticsTracker = RecordingAnalytics()) = GarageTelemetry(
    logger = NoopLogger,
    analytics = analytics,
    tracer = NoopTracer,
    ids = FixedIdGenerator("trace"),
)

/** A car with no documents — the garage reads all three sources, so all three need one. */
internal class FakeDocumentRepository(
    private val documents: List<Document> = emptyList(),
) : DocumentRepository {
    override fun observe(carId: CarId): Flow<List<Document>> = flowOf(documents)
    override fun observe(id: DocumentId): Flow<Document?> = flowOf(null)
    override suspend fun add(document: Document): Either<DomainError, Document> = document.right()
    override suspend fun update(document: Document): Either<DomainError, Document> = document.right()
    override suspend fun softDelete(id: DocumentId): Either<DomainError, Unit> = Unit.right()
    override suspend fun countForOwner(ownerId: OwnerId): Int = documents.size
}

/** A local DB that cannot be read — the failure the garage must say out loud. */
internal class FailingCarRepository : CarRepository {
    override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
    override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
    override suspend fun findByRegistration(ownerId: OwnerId, registrationNumber: RegistrationNumber): Car? = null
    override fun observePrimaryCar(): Flow<Car?> = observe(CarId("any"))
    override fun observe(id: CarId): Flow<Car?> = flow { throw IllegalStateException("db is gone") }
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()
}
