package com.hopcape.odo.feature.billscanner.presentation

import arrow.core.Either
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span

/** Test doubles for the bill scanner's ViewModels. */

/** Records what was tracked, so a test can assert on the event a screen is meant to emit. */
internal class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit

    /** How many times [name] was tracked. */
    fun count(name: String): Int = events.count { it.first == name }
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

private class FixedIdGenerator(private val id: String = "trace") : IdGenerator {
    override fun newId(): String = id
}

internal fun testTelemetry(analytics: AnalyticsTracker = RecordingAnalytics()) = BillScannerTelemetry(
    logger = NoopLogger,
    analytics = analytics,
    tracer = NoopTracer,
    ids = FixedIdGenerator(),
)

/** A file store no test in this package reaches: nothing here captures or picks a photo. */
internal object UnusedFileStore : PlatformFileStore {
    override suspend fun save(
        pickedRef: String,
        directory: String,
        fileName: String,
    ): Either<DomainError, String> = error("no test in this package saves a file")

    override suspend fun delete(storageKey: String) = error("no test in this package deletes a file")

    override suspend fun exists(storageKey: String) = error("no test in this package reads a file")

    override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
        error("no test in this package reads a file")

    override suspend fun write(storageKey: String, bytes: ByteArray): Either<DomainError, String> =
        error("no test in this package writes a file")
}
