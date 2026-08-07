package com.hopcape.odo.core.triptracker

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.internal.NoopTrackingPreconditions
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Confirms the graph [coreTripTrackerModule] declares actually resolves end to end. */
class CoreTripTrackerModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun tripTracker_resolvesToDefaultTripTracker() {
        val koin = graph().koin
        assertIs<DefaultTripTracker>(koin.get<TripTracker>())
    }

    @Test
    fun everyBindingInTheModule_resolves() {
        val koin = graph().koin
        koin.get<TripTracker>()
        koin.get<TripTrackerConfig>()
        koin.get<TripTrackerTelemetry>()
        koin.get<RouteDistanceEstimator>()
        koin.get<TripSessionStore>()
    }

    /**
     * Stands in for the four observability modules and the platform module's
     * [TrackingPreconditions] binding, so [coreTripTrackerModule] resolves the same way it
     * will once [tripTrackerAndroidModule] and the real graph are wired in.
     */
    private fun graph() = koinApplication {
        modules(
            module {
                single<Logger> { NoopLogger }
                single<AnalyticsTracker> { NoopAnalytics }
                single<PerformanceTracer> { NoopTracer }
                single<CrashRecorder> { NoopCrash }
                single<TrackingPreconditions> { NoopTrackingPreconditions() }
            },
            coreTripTrackerModule,
        )
    }
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

private object NoopAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
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

private object NoopCrash : CrashRecorder {
    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
    override fun leaveBreadcrumb(tag: String, message: String) = Unit
    override fun setCustomKey(key: String, value: Any?) = Unit
    override fun setUserId(userId: String?) = Unit
}
