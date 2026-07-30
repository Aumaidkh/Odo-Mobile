package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.onboarding.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.onboarding.navigation.OnboardingFeatureEntryProvider
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for onboarding. `CarRepository`/`VehicleCatalog` come from
 * `coreDataModule` and `NavigationManager` from `coreNavigationModule`; the `:app`
 * host registers them all. The owner provider is an M1 stub — the single swap
 * point for real auth.
 *
 * The [OnboardingFeatureEntryProvider] is bound to [FeatureEntryProvider] so the
 * host picks it up via `getAll<FeatureEntryProvider>()` and adds onboarding to the graph.
 *
 * One ViewModel per destination: [WelcomeViewModel] for the pitch, [OnboardingViewModel]
 * for the setup flow behind `OdoDestination.Onboarding`.
 */
val onboardingModule = module {
    single<IdGenerator> { UuidIdGenerator() }
    single<CurrentOwnerProvider> { LocalOwnerProvider() }
    factory { SaveCarUseCase(cars = get(), idGenerator = get()) }
    factory { LoadVehicleCatalogUseCase(catalog = get()) }
    factory { LoadCarModelsUseCase(catalog = get()) }
    factory { LookupPlateUseCase(registry = get()) }
    factory { CompleteOnboardingUseCase(profiles = get(), currentOwner = get()) }

    viewModel { WelcomeViewModel() }
    viewModel {
        OnboardingViewModel(
            loadCatalog = get(),
            loadModels = get(),
            lookupPlate = get(),
            saveCar = get(),
            completeOnboarding = get(),
            currentOwner = get(),
            // Published by :feature:auth via the shared :core:domain port — onboarding
            // asks whether to offer sign-in without knowing auth exists.
            sessionStatus = get(),
        )
    }

    single { OnboardingFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
