package com.hopcape.odo.core.data.appstatus

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.appstatus.observability.AppStatusTelemetry
import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatus
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import com.hopcape.odo.core.domain.appstatus.MaintenanceSeverity
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val T0 = Instant.fromEpochMilliseconds(1_700_000_000_000)
private const val CURRENT_VERSION = 100L

class DefaultAppStatusProviderTest {

    @Test
    fun `starts allowed before any refresh, fail open`() = runTest {
        val provider = newProvider(scope = backgroundScope)

        assertEquals(AppAvailability.Allowed, provider.availability.value)
    }

    @Test
    fun `refresh applies a fresh full block`() = runTest {
        val source = FakeAppStatusSource()
        val analytics = RecordingAnalytics()
        val provider = newProvider(source = source, analytics = analytics, scope = backgroundScope)
        source.next = statusOf(maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = T0)

        provider.refresh()

        assertEquals(AppAvailability.Blocked.Maintenance(null), provider.availability.value)
        assertEquals(
            mapOf("reason" to AppStatusTelemetry.REASON_MAINTENANCE),
            analytics.last(AppStatusTelemetry.EVENT_BLOCKED),
        )
    }

    @Test
    fun `a failed fetch leaves the previous verdict untouched`() = runTest {
        val source = FakeAppStatusSource()
        val provider = newProvider(source = source, scope = backgroundScope)
        source.next = statusOf(maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = T0)
        provider.refresh()
        check(provider.availability.value is AppAvailability.Blocked.Maintenance)

        source.next = null
        provider.refresh()

        assertEquals(AppAvailability.Blocked.Maintenance(null), provider.availability.value)
    }

    @Test
    fun `blocked then released reports exactly one of each, never for a degraded change`() = runTest {
        val source = FakeAppStatusSource()
        val analytics = RecordingAnalytics()
        val provider = newProvider(source = source, analytics = analytics, scope = backgroundScope)

        source.next = statusOf(maintenance = MaintenanceSeverity.DEGRADED, fetchedAt = T0)
        provider.refresh()
        assertNull(analytics.last(AppStatusTelemetry.EVENT_BLOCKED))
        assertNull(analytics.last(AppStatusTelemetry.EVENT_RELEASED))

        source.next = statusOf(maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = T0)
        provider.refresh()
        assertEquals(1, analytics.events.count { it.first == AppStatusTelemetry.EVENT_BLOCKED })

        source.next = statusOf(maintenance = MaintenanceSeverity.OFF, fetchedAt = T0)
        provider.refresh()
        assertEquals(1, analytics.events.count { it.first == AppStatusTelemetry.EVENT_RELEASED })
    }

    @Test
    fun `a stale maintenance verdict decays to allowed on its own recheck cadence`() = runTest {
        val source = FakeAppStatusSource()
        val analytics = RecordingAnalytics()
        val clock = MutableClock(T0)
        val recheckInterval = 1.minutes
        val trustWindow = 30.minutes
        val provider = newProvider(
            source = source,
            analytics = analytics,
            clock = clock,
            trustWindow = trustWindow,
            recheckInterval = recheckInterval,
            scope = backgroundScope,
        )
        source.next = statusOf(maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = T0)
        provider.refresh()
        check(provider.availability.value is AppAvailability.Blocked.Maintenance)

        // No further refresh(): only the wall clock moves past the trust window and the
        // provider's own recheck loop ticks — this is D4's self-release, which nothing
        // else in the app would ever trigger for a build sitting on the block screen.
        clock.now = T0 + trustWindow + 1.minutes
        advanceTimeBy(recheckInterval + 1.minutes)

        assertEquals(AppAvailability.Allowed, provider.availability.value)
        assertEquals(1, analytics.events.count { it.first == AppStatusTelemetry.EVENT_RELEASED })
    }

    @Test
    fun `update required blocks regardless of how stale the fetch is`() = runTest {
        val source = FakeAppStatusSource()
        val provider = newProvider(source = source, scope = backgroundScope)
        source.next = statusOf(minVersion = CURRENT_VERSION + 1, maintenance = MaintenanceSeverity.OFF, fetchedAt = null)

        provider.refresh()

        assertEquals(AppAvailability.Blocked.UpdateRequired, provider.availability.value)
    }

    private fun statusOf(
        minVersion: Long = 0L,
        maintenance: MaintenanceSeverity,
        fetchedAt: Instant?,
    ) = AppStatus(
        minSupportedVersionCode = minVersion,
        maintenance = maintenance,
        maintenanceMessage = null,
        fetchedAt = fetchedAt,
    )

    private fun newProvider(
        source: AppStatusSource = FakeAppStatusSource(),
        analytics: AnalyticsTracker = RecordingAnalytics(),
        clock: MutableClock = MutableClock(T0),
        trustWindow: kotlin.time.Duration = 30.minutes,
        recheckInterval: kotlin.time.Duration = 1.minutes,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = DefaultAppStatusProvider(
        source = source,
        appInfo = FixedAppInfo,
        clock = clock,
        telemetry = AppStatusTelemetry(logger = NoopLogger, analytics = analytics, tracer = NoopTracer),
        trustWindow = trustWindow,
        recheckInterval = recheckInterval,
        scope = scope,
    )

    private class FakeAppStatusSource : AppStatusSource {
        var next: AppStatus? = null
        override suspend fun fetch(): AppStatus? = next
    }

    private class MutableClock(initial: Instant) : kotlin.time.Clock {
        var now: Instant = initial
        override fun now(): Instant = now
    }

    private object FixedAppInfo : AppInfo {
        override val versionName: String = "1.0"
        override val versionCode: Long = CURRENT_VERSION
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
