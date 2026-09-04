package com.hopcape.odo.feature.advisory

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.advisory.domain.ObserveCarValueUseCase
import com.hopcape.odo.feature.advisory.navigation.AdvisoryFeatureEntryProvider
import com.hopcape.odo.feature.advisory.presentation.AdvisoryTelemetry
import com.hopcape.odo.feature.advisory.presentation.CarValueViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the advisory screens — today, "my car's value".
 *
 * The only public declaration in the module, per the minimal-surface rule: everything it
 * builds is `internal`, and Koin resolves by type regardless.
 */
val advisoryModule = module {

    factory {
        ObserveCarValueUseCase(
            cars = get(),
            logs = get(),
            profiles = get(),
            cities = get(),
            clock = get(),
            telemetry = get(),
        )
    }

    // A factory, so one instance covers one visit to the screen.
    factory { AdvisoryTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { CarValueViewModel(observeCarValue = get(), telemetry = get()) }

    single { AdvisoryFeatureEntryProvider(navigationManager = get()) } bind FeatureEntryProvider::class
}
