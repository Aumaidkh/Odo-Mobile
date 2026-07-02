package com.hopcape.analytics

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.HAnalytics
import com.hopcape.analytics.api.analyticsModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Integration test for the DI wiring: `analyticsModule` must republish the
 * facade's single tracker, so `koinInject<AnalyticsTracker>()` and static
 * `HAnalytics.track(...)` resolve to the same underlying pipeline — one config,
 * one dispatch queue, one set of destinations.
 */
class AnalyticsKoinIntegrationTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bindsTrackerAsSingleton() {
        val koin = startKoin { modules(analyticsModule) }.koin
        assertSame(koin.get<AnalyticsTracker>(), koin.get<AnalyticsTracker>(), "tracker must be a single")
    }

    @Test
    fun injectedTracker_isTheFacadesTracker() {
        val koin = startKoin { modules(analyticsModule) }.koin
        assertSame(
            HAnalytics.asTracker(),
            koin.get<AnalyticsTracker>(),
            "analyticsModule must republish HAnalytics.asTracker(), not build its own tracker",
        )
    }

    @Test
    fun injectedTracker_isUsableBeforeInit_withoutThrowing() {
        val koin = startKoin { modules(analyticsModule) }.koin
        // Before HAnalytics.init this routes to the no-op fallback — must not throw.
        koin.get<AnalyticsTracker>().track("wired")
    }
}
