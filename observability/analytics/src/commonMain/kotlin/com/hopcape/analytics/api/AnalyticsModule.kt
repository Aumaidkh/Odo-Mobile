package com.hopcape.analytics.api

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI graph for analytics — the module's Koin entry point alongside the
 * [HAnalytics] facade. Rather than building its own tracker, it **republishes**
 * the facade's underlying [AnalyticsTracker] ([HAnalytics.asTracker]) into the
 * graph. So DI consumers (`koinInject<AnalyticsTracker>()` / constructor
 * injection) and static `HAnalytics.track(...)` calls resolve to ONE configured
 * tracker — one config, one dispatch queue, one set of destinations.
 *
 * Configuration therefore lives in exactly one place: the app's single
 * [HAnalytics.init] call at startup. Callers only ever see the
 * [AnalyticsTracker] interface; every destination / dispatcher / store stays
 * `internal` to this module.
 */
val analyticsModule: Module = module {
    single<AnalyticsTracker> { HAnalytics.asTracker() }
}
