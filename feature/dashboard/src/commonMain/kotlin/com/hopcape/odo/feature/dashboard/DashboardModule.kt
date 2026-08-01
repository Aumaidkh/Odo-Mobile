package com.hopcape.odo.feature.dashboard

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.dashboard.domain.usecase.ObserveHomeUseCase
import com.hopcape.odo.feature.dashboard.navigation.DashboardFeatureEntryProvider
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTelemetry
import com.hopcape.odo.feature.dashboard.presentation.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the dashboard feature. The repositories, the providers and the `Clock` come
 * from `coreDataModule`, and `NavigationManager` from `coreNavigationModule`; the `:app`
 * host registers them all.
 *
 * [DashboardFeatureEntryProvider] is bound to [FeatureEntryProvider] so the `:app` host
 * picks it up via `getAll<FeatureEntryProvider>()` and the bottom-nav roots resolve.
 *
 * The shell composable ([presentation.shell.OdoAppScaffold]) is called directly by
 * the composition root, not resolved through Koin.
 */
val dashboardModule = module {
    single { DashboardFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class

    factory {
        ObserveHomeUseCase(
            cars = get(),
            logs = get(),
            documents = get(),
            scores = get(),
            owners = get(),
            city = get(),
            fuelPrices = get(),
            clock = get(),
        )
    }

    // A `factory`, not a `single`: one instance covers one visit to the tab, and every event
    // of that visit shares one flow id.
    factory { HomeTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModelOf(::HomeViewModel)
}
