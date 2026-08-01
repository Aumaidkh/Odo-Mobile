package com.hopcape.odo.feature.fairnesscheck

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.fairnesscheck.navigation.FairnessCheckFeatureEntryProvider
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessCheckInput
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessTelemetry
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the reusable fairness-check feature.
 *
 * The `FairnessAnalyzer` is **not** bound here: it comes from `coreDataModule`, which reads
 * the benchmarks the rest of the app reads. A feature-local stand-in would be a second set
 * of numbers, and two screens quoting different city averages is the one thing this feature
 * cannot afford. `CurrentCityProvider` comes from there too.
 */
val fairnessCheckModule = module {

    // A `factory`, not a `single`: each instance mints its own trace id, so one instance
    // covers one check.
    factory { FairnessTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModel { params ->
        FairnessViewModel(
            input = params.get<FairnessCheckInput>(),
            analyzer = get(),
            city = get(),
            telemetry = get(),
        )
    }

    single {
        FairnessCheckFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
