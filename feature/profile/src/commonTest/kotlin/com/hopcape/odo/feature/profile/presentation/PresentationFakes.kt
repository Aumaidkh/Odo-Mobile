package com.hopcape.odo.feature.profile.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span

/** Test doubles for the profile's ViewModels. */

/** Records what was tracked, so a test can assert on the event a screen is meant to emit. */
internal class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit

    /** The properties of the last [name] event, or null if it was never tracked. */
    fun last(name: String): Map<String, Any?>? = events.lastOrNull { it.first == name }?.second
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

internal fun testTelemetry(analytics: AnalyticsTracker = RecordingAnalytics()) = ProfileTelemetry(
    logger = NoopLogger,
    analytics = analytics,
    tracer = NoopTracer,
    ids = FixedIdGenerator(),
)

internal class FakeAppInfo(override val versionName: String = "1.4.0") : AppInfo
