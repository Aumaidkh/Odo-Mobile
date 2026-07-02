package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import com.hopcape.odo.core.domain.car.usecase.AddCarUseCase
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.onboarding.navigation.OnboardingFeatureEntryProvider
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.scan.HistoryScanLauncher
import com.hopcape.odo.feature.onboarding.presentation.scan.StubHistoryScanLauncher
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for onboarding. `CarRepository`/`VehicleCatalog` come from
 * `coreDataModule` and `NavigationManager` from `coreNavigationModule`; the `:app`
 * host registers them all. The owner provider and scan launcher are M1 stubs — the
 * single swap point for real auth / the M2 scanner.
 *
 * The [OnboardingFeatureEntryProvider] is bound to [FeatureEntryProvider] so the
 * host picks it up via `getAll<FeatureEntryProvider>()` and adds onboarding to the graph.
 */
val onboardingModule = module {
    single<IdGenerator> { UuidIdGenerator() }
    single<CurrentOwnerProvider> { LocalOwnerProvider() }
    single<HistoryScanLauncher> { StubHistoryScanLauncher() }
    factory { AddCarUseCase(cars = get(), idGenerator = get()) }
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::WelcomeViewModel)
    single { OnboardingFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
