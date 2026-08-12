package com.hopcape.odo.feature.healthscore

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.healthscore.domain.usecase.ObserveHealthScoreUseCase
import com.hopcape.odo.feature.healthscore.domain.usecase.RecordHealthScoreUseCase
import com.hopcape.odo.feature.healthscore.navigation.HealthScoreFeatureEntryProvider
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreTelemetry
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the health-score feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * The [HealthScoreFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host
 * discovers it via `getAll<FeatureEntryProvider>()`. The repositories, the entitlement
 * port, `IdGenerator` and `Clock` come from `coreDataModule` and `coreCommonModule`; the
 * health-score ViewModel joins here in the next slice.
 */
val healthScoreModule = module {
    single {
        HealthScoreFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory {
        ObserveHealthScoreUseCase(
            logs = get(),
            documents = get(),
            snapshots = get(),
            entitlement = get(),
            currentOdometer = get(),
            clock = get(),
        )
    }
    factory {
        RecordHealthScoreUseCase(snapshots = get(), owners = get(), ids = get(), clock = get())
    }

    // A `factory`, not a `single`: one instance covers one visit to the screen, and every
    // event of that visit shares one flow id.
    factory { HealthScoreTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModelOf(::HealthScoreViewModel)
}
