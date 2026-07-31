package com.hopcape.odo.feature.garage

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.garage.domain.usecase.AddCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.GetOdometerContextUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.RemoveCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.UpdateCarDetailsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.UpdateOdometerUseCase
import com.hopcape.odo.feature.garage.navigation.GarageFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the garage feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * [GarageFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks
 * it up via `getAll<FeatureEntryProvider>()` and the Garage bottom-nav root resolves.
 * The garage aggregation ViewModel + use cases (reading `:core:domain` ports) join
 * here as the feature is built.
 */
val garageModule = module {
    single {
        GarageFeatureEntryProvider(navigationManager = get(), activeCar = get())
    } bind FeatureEntryProvider::class

    factory {
        ObserveGarageUseCase(cars = get(), documents = get(), logs = get(), clock = get())
    }
    factory { UpdateOdometerUseCase(cars = get(), logs = get(), clock = get()) }
    factory { GetOdometerContextUseCase(logs = get(), clock = get()) }
    factory { AddCarUseCase(cars = get(), idGenerator = get(), owner = get()) }
    factory { UpdateCarDetailsUseCase(cars = get()) }
    factory { RemoveCarUseCase(cars = get()) }
    factory { LoadVehicleCatalogUseCase(catalog = get()) }
    factory { LoadCarModelsUseCase(catalog = get()) }
    factory { LookupPlateUseCase(registry = get()) }
}
