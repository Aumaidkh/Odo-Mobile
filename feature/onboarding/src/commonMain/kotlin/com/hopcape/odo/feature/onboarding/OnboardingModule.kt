package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.onboarding.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.onboarding.navigation.OnboardingFeatureEntryProvider
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
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

    // OnboardingConfig's generated bindings. Folded in here so initKoin's list does not
    // grow with every group, and so installing the feature is what installs its config.
    includes(onboardingConfigModule)
    factory { SaveCarUseCase(cars = get(), logs = get(), idGenerator = get(), clock = get()) }
    factory { LoadVehicleCatalogUseCase(catalog = get()) }
    factory { LoadCarModelsUseCase(catalog = get()) }
    factory { LookupPlateUseCase(registry = get()) }
    factory { CompleteOnboardingUseCase(profiles = get(), currentOwner = get()) }

    // A `factory`, not a `single`: each instance mints its own trace id, so one instance covers
    // one attempt at the journey. Both first-run ViewModels share the flow id regardless, which
    // is what stitches the welcome pitch and the setup steps into one funnel.
    // The Logger / AnalyticsTracker / PerformanceTracer come from the :observability:* modules,
    // whose single configuration is owned by the app bootstrap.
    factory { OnboardingTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { WelcomeViewModel(telemetry = get()) }
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
            telemetry = get(),
        )
    }

    single {
        OnboardingFeatureEntryProvider(
            navigationManager = get(),
            // Where the hosted Terms and Privacy Policy live, for the two links in the
            // welcome footer. Bound by `supabaseModule` from the configured project URL and
            // overridable from Remote Config; a build with no backend gets blanks.
            legalLinks = get(),
        )
    } bind FeatureEntryProvider::class
}
