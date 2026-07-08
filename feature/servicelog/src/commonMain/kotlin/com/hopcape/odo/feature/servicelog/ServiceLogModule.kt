package com.hopcape.odo.feature.servicelog

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.servicelog.navigation.ServiceLogFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the service-log feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [ServiceLogFeatureEntryProvider] is bound to [FeatureEntryProvider] so the
 * host picks it up via `getAll<FeatureEntryProvider>()` and adds servicelog to the
 * graph. ViewModels / telemetry / use cases join here as the feature is built.
 */
val serviceLogModule = module {
    single {
        ServiceLogFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
