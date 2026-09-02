package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.onboarding.navigation.OnboardingFeatureEntryProvider
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import com.hopcape.odo.feature.onboarding.presentation.video.WelcomeVideoViewModel
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the first-run pitch — the Welcome page and the video intro.
 *
 * Car setup lives in `:feature:questionnaire` with its own module and ViewModel. The two are
 * joined only by `OdoDestination.Onboarding`, which this one navigates to and that one serves.
 */
val onboardingModule = module {

    // OnboardingConfig's generated bindings. Folded in here so initKoin's list does not grow
    // with every group, and so installing the feature installs its config.
    includes(onboardingConfigModule)

    // A factory, so one instance covers one visit to the pitch.
    factory { OnboardingTelemetry(logger = get(), analytics = get(), ids = get()) }

    viewModel { WelcomeVideoViewModel(config = get()) }
    viewModel { WelcomeViewModel(telemetry = get()) }

    single {
        OnboardingFeatureEntryProvider(
            navigationManager = get(),
            // Where the hosted Terms and Privacy Policy live, for the welcome footer's links.
            legalLinks = get(),
        )
    } bind FeatureEntryProvider::class
}
