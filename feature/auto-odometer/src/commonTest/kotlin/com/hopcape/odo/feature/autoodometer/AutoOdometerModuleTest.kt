package com.hopcape.odo.feature.autoodometer

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.feature.autoodometer.di.autoOdometerModule
import com.hopcape.odo.feature.autoodometer.domain.usecase.AcknowledgeTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.CompleteSetup
import com.hopcape.odo.feature.autoodometer.domain.usecase.DeleteAllTripData
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeAppSettingsRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeCurrentOdometerProvider
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTrackingPreconditions
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripTracker
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeVehicleBondStore
import com.hopcape.odo.feature.autoodometer.domain.usecase.READY
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveDerivedOdometer
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveMonthlySummary
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObservePendingTripLogged
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveServiceDueNudge
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveSetupState
import com.hopcape.odo.feature.autoodometer.domain.usecase.PauseTracking
import com.hopcape.odo.feature.autoodometer.domain.usecase.RejectTrip
import com.hopcape.odo.feature.autoodometer.domain.usecase.ResumeTracking
import com.hopcape.odo.feature.autoodometer.navigation.AutoOdometerFeatureEntryProvider
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.FakeActiveCarProvider
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Confirms [autoOdometerModule] resolves end to end. F2 wired only the entry provider and
 * the telemetry facade; F3 adds every use case from plan §4.1, so this now also stands in
 * for `:core:triptracker`'s and `:core:data`'s ports with fakes.
 */
class AutoOdometerModuleTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun featureEntryProvider_resolvesToAutoOdometerFeatureEntryProvider() {
        val koin = graph().koin
        assertIs<AutoOdometerFeatureEntryProvider>(koin.get<FeatureEntryProvider>())
    }

    @Test
    fun everyBindingInTheModule_resolves() {
        val koin = graph().koin
        koin.get<FeatureEntryProvider>()
        koin.get<AutoOdometerTelemetry>()
        koin.get<ObserveSetupState>()
        koin.get<EnrollTriggerDevice>()
        koin.get<CompleteSetup>()
        koin.get<ObservePendingTripLogged>()
        koin.get<AcknowledgeTrip>()
        koin.get<RejectTrip>()
        koin.get<ObserveDerivedOdometer>()
        koin.get<ObserveMonthlySummary>()
        koin.get<PauseTracking>()
        koin.get<ResumeTracking>()
        koin.get<DeleteAllTripData>()
        koin.get<ObserveServiceDueNudge>()
        koin.get<PendingTripLoggedProvider>()
    }

    /** Stands in for the four observability modules, `coreNavigationModule`, `coreTripTrackerModule`,
     * `coreDataModule` and `coreCommonModule`. */
    private fun graph() = koinApplication {
        modules(
            module {
                single<Logger> { NoopLogger }
                single<AnalyticsTracker> { NoopAnalytics }
                single<PerformanceTracer> { NoopTracer }
                single<CrashRecorder> { NoopCrash }
                single<NavigationManager> { NavigationManager() }
                single { UuidIdGenerator() }
                single<IdGenerator> { get<UuidIdGenerator>() }
                single<Clock> { Clock.System }
                single<ServiceLogRepository> { FakeServiceLogRepository() }
                single<CurrentOdometerProvider> { FakeCurrentOdometerProvider(null) }
                single<TripRepository> { FakeTripRepository() }
                single<AppSettingsRepository> { FakeAppSettingsRepository() }
                single<VehicleBondStore> { FakeVehicleBondStore() }
                single<TripTracker> { FakeTripTracker() }
                single<TrackingPreconditions> { FakeTrackingPreconditions(READY) }
                single<ActiveCarProvider> { FakeActiveCarProvider(TEST_CAR) }
            },
            autoOdometerModule,
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
