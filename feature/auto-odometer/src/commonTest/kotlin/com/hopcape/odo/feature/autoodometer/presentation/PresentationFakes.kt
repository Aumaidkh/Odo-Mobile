package com.hopcape.odo.feature.autoodometer.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Test doubles for the auto-odometer feature's ViewModels. */

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

internal object NoopCrash : CrashRecorder {
    val recorded = mutableListOf<Throwable>()
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
        recorded += throwable
    }
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}

/** A per-instance [CrashRecorder] fake, for tests that need to isolate what they recorded (unlike the singleton [NoopCrash]). */
internal class RecordingCrash : CrashRecorder {
    val recorded = mutableListOf<Pair<Throwable, Map<String, Any?>>>()
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
        recorded += throwable to customKeys
    }
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}

private class FixedIdGenerator(private val id: String = "trace") : IdGenerator {
    override fun newId(): String = id
}

internal fun testTelemetry(
    analytics: AnalyticsTracker = RecordingAnalytics(),
    crash: CrashRecorder = NoopCrash,
) = AutoOdometerTelemetry(
    logger = NoopLogger,
    analytics = analytics,
    tracer = NoopTracer,
    crash = crash,
    ids = FixedIdGenerator(),
)

/** A fixed (or missing) active car — [UpdateOdometerViewModelTest]'s `FakeActiveCar` shape. */
internal class FakeActiveCarProvider(carId: CarId?) : ActiveCarProvider {
    private val _id = MutableStateFlow(carId)
    override val activeCarId: StateFlow<CarId?> = _id
}

/** Answers [devices] once per call; `throwing` simulates a Bluetooth-adapter read failure. */
internal class FakeBondedDeviceCatalog(
    private val devices: List<BondedDevice> = emptyList(),
    private val throwing: Boolean = false,
) : BondedDeviceCatalog {
    var callCount = 0
        private set

    override suspend fun devices(): List<BondedDevice> {
        callCount++
        if (throwing) error("bluetooth adapter unavailable")
        return devices
    }
}
