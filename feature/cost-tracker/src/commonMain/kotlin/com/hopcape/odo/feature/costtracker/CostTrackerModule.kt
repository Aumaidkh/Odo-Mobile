package com.hopcape.odo.feature.costtracker

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.costtracker.navigation.CostTrackerFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the cost-tracker feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [CostTrackerFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host
 * picks it up via `getAll<FeatureEntryProvider>()`. The running-cost ViewModel + the
 * ledger-aggregation use cases join here as the feature is built.
 */
val costTrackerModule = module {
    single {
        CostTrackerFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
