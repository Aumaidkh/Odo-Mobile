package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.common.id.UuidIdGenerator
import com.hopcape.odo.core.domain.car.usecase.AddCarUseCase
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.feature.onboarding.presentation.HistoryScanLauncher
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.StubHistoryScanLauncher
import org.koin.dsl.module

/**
 * DI graph for onboarding. `CarRepository` and `VehicleCatalog` come from
 * `coreDataModule`; the `:app` host registers both. The owner provider and scan
 * launcher are M1 stubs — the single swap point for real auth / the M2 scanner.
 */
val onboardingModule = module {
    single<IdGenerator> { UuidIdGenerator() }
    single<CurrentOwnerProvider> { LocalOwnerProvider() }
    single<HistoryScanLauncher> { StubHistoryScanLauncher() }
    factory { AddCarUseCase(cars = get(), idGenerator = get()) }
    factory { OnboardingViewModel(get(), get(), get(), get(), get()) }
}
