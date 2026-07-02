package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span

/** No-op [Logger] for tests — swallows every log call. */
object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) {}

    override fun flush() {}
}

/** Records log calls with their [TraceContext] so a test can assert trace correlation. */
class RecordingLogger : Logger {
    data class Entry(
        val level: LogLevel,
        val tag: String,
        val event: String,
        val trace: TraceContext?,
    )

    val entries = mutableListOf<Entry>()

    /** The first recorded log with [event], or null if it was never emitted. */
    fun entry(event: String): Entry? = entries.firstOrNull { it.event == event }

    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) {
        entries += Entry(level, tag, event, traceContext)
    }

    override fun flush() {}
}

/** Records tracked events so a test can assert the analytics funnel actually fired. */
class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()

    /** Just the event names, in order — the common assertion target. */
    val names: List<String> get() = events.map { it.first }

    /** Properties of the last event with [eventName], or null if it was never tracked. */
    fun propsOf(eventName: String): Map<String, Any?>? =
        events.lastOrNull { it.first == eventName }?.second

    override fun identify(traits: UserTraits) {}
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }

    override fun setConsent(status: ConsentStatus) {}
    override fun flush() {}
}

/** No-op [PerformanceTracer] for tests — hands back inert spans, records nothing. */
object NoopTracer : PerformanceTracer {
    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        NoopSpan(name, traceId, parentSpanId)

    override fun endSpan(span: Span) {}
    override fun flush() {}
}

private class NoopSpan(
    override val name: String,
    override val traceId: String,
    override val parentSpanId: String?,
) : Span {
    override val spanId: String = "test-span"
    override fun setAttribute(key: String, value: Any?): Span = this
}
