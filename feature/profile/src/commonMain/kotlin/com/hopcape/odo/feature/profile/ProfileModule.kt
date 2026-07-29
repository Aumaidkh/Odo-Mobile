package com.hopcape.odo.feature.profile

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.profile.navigation.ProfileFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the profile feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * [ProfileFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it
 * up via `getAll<FeatureEntryProvider>()` and the profile destinations resolve.
 */
val profileModule = module {
    single {
        ProfileFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
