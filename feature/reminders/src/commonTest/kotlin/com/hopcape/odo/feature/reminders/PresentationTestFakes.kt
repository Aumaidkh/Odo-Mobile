package com.hopcape.odo.feature.reminders

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.feature.reminders.presentation.RemindersTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.MutableStateFlow

/** A fixed active car — the presentation tests never change cars mid-flight. */
internal class FakeActiveCarProvider(
    car: CarId? = TEST_CAR,
) : ActiveCarProvider {
    override val activeCarId = MutableStateFlow(car)
}

/** Records tracked events so a test can assert an outcome was counted. */
internal class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<String>()
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName
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

/** A real facade over silent ports — behaviour tests care what happened, not what was logged. */
internal fun silentRemindersTelemetry(
    analytics: AnalyticsTracker = RecordingAnalytics(),
): RemindersTelemetry = RemindersTelemetry(
    logger = NoopLogger,
    analytics = analytics,
    tracer = NoopTracer,
    ids = FixedIdGenerator("trace"),
)
