package com.hopcape.odo.core.data.appstatus

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.appstatus.observability.AppStatusTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStatusTelemetryTest {

    private val analytics = RecordingAnalytics()
    private val logger = RecordingLogger()
    private val telemetry = AppStatusTelemetry(logger = logger, analytics = analytics, tracer = NoopTracer)

    @Test
    fun `blocked tracks the reason and logs it`() {
        telemetry.blocked(AppStatusTelemetry.REASON_MAINTENANCE)

        assertEquals(
            mapOf("reason" to AppStatusTelemetry.REASON_MAINTENANCE),
            analytics.last(AppStatusTelemetry.EVENT_BLOCKED),
        )
        assertTrue(logger.events.any { it == AppStatusTelemetry.EVENT_BLOCKED })
    }

    @Test
    fun `released tracks with no properties`() {
        telemetry.released()

        assertEquals(emptyMap(), analytics.last(AppStatusTelemetry.EVENT_RELEASED))
    }

    @Test
    fun `fetchFailed logs but never tracks an analytics event`() {
        telemetry.fetchFailed()

        assertTrue(logger.events.any { it == AppStatusTelemetry.EVENT_FETCH_FAILED })
        assertEquals(null, analytics.last(AppStatusTelemetry.EVENT_FETCH_FAILED))
    }

    @Test
    fun `refresh spans the block and returns its result untouched`() = runTest {
        val result = telemetry.refresh { "fetched" }

        assertEquals("fetched", result)
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
        fun last(name: String): Map<String, Any?>? = events.lastOrNull { it.first == name }?.second
    }

    private class RecordingLogger : Logger {
        val events = mutableListOf<String>()
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) {
            events += event
        }
        override fun flush() = Unit
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            object : Span {
                override val spanId = "span"
                override val traceId = traceId
                override val parentSpanId = parentSpanId
                override val name = name
                override fun setAttribute(key: String, value: Any?): Span = this
            }
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }
}
