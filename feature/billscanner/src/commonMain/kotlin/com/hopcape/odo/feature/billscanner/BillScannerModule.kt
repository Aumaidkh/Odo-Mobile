package com.hopcape.odo.feature.billscanner

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.billscanner.navigation.BillScannerFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the bill-scanner feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [BillScannerFeatureEntryProvider] is bound to [FeatureEntryProvider] so the
 * host picks it up via `getAll<FeatureEntryProvider>()` and adds the scan screen to
 * the graph. The scan ViewModel + AI use cases join here as the feature is built.
 */
val billScannerModule = module {
    single {
        BillScannerFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
