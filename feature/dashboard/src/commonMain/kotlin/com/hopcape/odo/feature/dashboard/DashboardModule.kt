package com.hopcape.odo.feature.dashboard

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.dashboard.navigation.DashboardFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the dashboard feature. Binds [DashboardFeatureEntryProvider] to
 * [FeatureEntryProvider] so the `:app` host picks it up via
 * `getAll<FeatureEntryProvider>()` and the bottom-nav roots resolve.
 *
 * The shell composable ([presentation.shell.OdoAppScaffold]) is called directly by
 * the composition root, not resolved through Koin.
 */
val dashboardModule = module {
    single { DashboardFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
