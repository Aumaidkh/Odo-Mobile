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
import com.hopcape.odo.feature.garage.presentation.AddCarViewModel
import com.hopcape.odo.feature.garage.presentation.EditCarViewModel
import com.hopcape.odo.feature.garage.presentation.GarageTelemetry
import com.hopcape.odo.feature.garage.presentation.GarageViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.ExportViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.UpdateOdometerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the garage feature. `NavigationManager` comes from `coreNavigationModule`,
 * the repositories and `ActiveCarProvider` from `coreDataModule`, and `Clock` +
 * `IdGenerator` from `coreCommonModule`; the `:app` host registers them all.
 *
 * [GarageFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it up
 * via `getAll<FeatureEntryProvider>()` and the Garage bottom-nav root resolves.
 */
val garageModule = module {
    single {
        GarageFeatureEntryProvider(navigationManager = get())
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

    // A `factory`, not a `single`: one instance covers one visit to the garage, and every
    // screen of that visit shares its flow id.
    factory { GarageTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { GarageViewModel(activeCar = get(), observeGarage = get(), telemetry = get()) }
    viewModel {
        UpdateOdometerViewModel(
            activeCar = get(),
            getContext = get(),
            updateOdometer = get(),
            telemetry = get(),
        )
    }
    viewModel {
        AddCarViewModel(
            addCar = get(),
            loadCatalog = get(),
            loadModels = get(),
            lookupPlate = get(),
            telemetry = get(),
        )
    }
    viewModel {
        EditCarViewModel(
            activeCar = get(),
            observeGarage = get(),
            updateDetails = get(),
            loadCatalog = get(),
            loadModels = get(),
            telemetry = get(),
        )
    }
    viewModel { CarActionsViewModel(activeCar = get(), observeGarage = get(), telemetry = get()) }
    viewModel {
        RemoveCarViewModel(
            activeCar = get(),
            observeGarage = get(),
            removeCar = get(),
            telemetry = get(),
        )
    }
    viewModel { ExportViewModel(activeCar = get(), observeGarage = get(), telemetry = get()) }
}
