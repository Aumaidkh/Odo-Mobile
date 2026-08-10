package com.hopcape.odo.feature.support

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.support.navigation.SupportFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the support feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * [SupportFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it up
 * via `getAll<FeatureEntryProvider>()` and the support destinations resolve.
 */
val supportModule = module {
    single {
        SupportFeatureEntryProvider(
            navigationManager = get(),
            logUploadScheduler = get(),
            // AppInfo comes from corePlatformModule — the version shown on the help sheet.
            appInfo = get(),
        )
    } bind FeatureEntryProvider::class
}
