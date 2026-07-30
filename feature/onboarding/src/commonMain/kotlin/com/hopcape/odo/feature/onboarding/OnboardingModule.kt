package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.onboarding.domain.usecase.AddCarUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.onboarding.navigation.OnboardingFeatureEntryProvider
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
 * ViewModel/telemetry definitions were removed with the old first-run screens —
 * re-register the redesigned flow's ViewModels here as they land.
 */
val onboardingModule = module {
    single<IdGenerator> { UuidIdGenerator() }
    single<CurrentOwnerProvider> { LocalOwnerProvider() }
    factory { AddCarUseCase(cars = get(), idGenerator = get()) }
    factory { LoadVehicleCatalogUseCase(catalog = get()) }
    factory { LoadCarModelsUseCase(catalog = get()) }
    // LookupPlateUseCase and CompleteOnboardingUseCase are deliberately NOT registered
    // yet: their ports (VehicleRegistryLookup, OwnerProfileRepository) have no adapter in
    // the graph, so a definition here would resolve to a crash rather than a missing
    // feature. They are registered in the slice that ships those adapters.
    single {
        OnboardingFeatureEntryProvider(
            navigationManager = get(),
            // Published by :feature:auth via the shared :core:domain port — onboarding
            // asks whether to offer sign-in without knowing auth exists.
            sessionStatus = get(),
        )
    } bind FeatureEntryProvider::class
}
