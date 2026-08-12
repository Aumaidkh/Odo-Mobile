package com.hopcape.odo.feature.timeline

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.timeline.domain.usecase.ObserveTimelineUseCase
import com.hopcape.odo.feature.timeline.navigation.TimelineFeatureEntryProvider
import com.hopcape.odo.feature.timeline.presentation.TimelineFilterStore
import com.hopcape.odo.feature.timeline.presentation.TimelineTelemetry
import com.hopcape.odo.feature.timeline.presentation.TimelineViewModel
import com.hopcape.odo.feature.timeline.presentation.sheets.TimelineFilterViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the timeline feature. `NavigationManager` comes from `coreNavigationModule`
 * and the repositories from `coreDataModule`; the `:app` host registers them all.
 *
 * [TimelineFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks it up
 * via `getAll<FeatureEntryProvider>()` and the Timeline bottom-nav root resolves.
 */
val timelineModule = module {
    single {
        TimelineFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class

    factory {
        ObserveTimelineUseCase(cars = get(), logs = get(), documents = get(), scores = get())
    }

    // A `single`: the tab and its sheet are two destinations looking at one choice, so they
    // have to share the instance holding it.
    single { TimelineFilterStore() }

    // A `factory`, not a `single`: one instance covers one visit to the tab, and every event
    // of that visit shares one flow id.
    factory { TimelineTelemetry(logger = get(), analytics = get(), tracer = get(), ids = get()) }

    viewModelOf(::TimelineViewModel)
    viewModelOf(::TimelineFilterViewModel)
}
