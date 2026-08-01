package com.hopcape.odo.feature.costtracker

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.costtracker.domain.usecase.ClearFuelRateUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.ObserveRunningCostUseCase
import com.hopcape.odo.feature.costtracker.domain.usecase.SetFuelRateUseCase
import com.hopcape.odo.feature.costtracker.navigation.CostTrackerFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the cost-tracker feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [CostTrackerFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host
 * picks it up via `getAll<FeatureEntryProvider>()`. The repositories, the fuel-price ports
 * and `Clock` come from `coreDataModule` and `coreCommonModule`; the running-cost ViewModel
 * joins here in the next slice.
 */
val costTrackerModule = module {
    single {
        CostTrackerFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory {
        ObserveRunningCostUseCase(
            cars = get(),
            logs = get(),
            city = get(),
            fuelPrices = get(),
            clock = get(),
        )
    }
    factory { SetFuelRateUseCase(overrides = get(), clock = get()) }
    factory { ClearFuelRateUseCase(overrides = get()) }
}
