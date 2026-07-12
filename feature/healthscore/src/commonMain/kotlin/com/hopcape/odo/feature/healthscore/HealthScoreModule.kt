package com.hopcape.odo.feature.healthscore

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.healthscore.navigation.HealthScoreFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the health-score feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [HealthScoreFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host
 * discovers it via `getAll<FeatureEntryProvider>()`. The health-score ViewModel + the
 * rule-based scoring use case join here as the feature is built out.
 */
val healthScoreModule = module {
    single {
        HealthScoreFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
