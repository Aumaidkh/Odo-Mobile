package com.hopcape.odo.infrastructure.firebase.analytics

import com.hopcape.analytics.api.AnalyticsSink
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI entry point for the Firebase destination. Bound to the public
 * [AnalyticsSink] port so a consumer never needs to know the concrete
 * Firebase type — everything else about this module (the sink, the
 * sanitizer, the gateway) stays `internal`.
 */
val firebaseAnalyticsModule: Module = module {
    single<AnalyticsSink> { FirebaseAnalyticsSink() }
}
