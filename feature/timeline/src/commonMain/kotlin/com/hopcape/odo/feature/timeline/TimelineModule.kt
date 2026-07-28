package com.hopcape.odo.feature.timeline

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.timeline.navigation.TimelineFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the timeline feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all.
 *
 * [TimelineFeatureEntryProvider] is bound to [FeatureEntryProvider] so the host picks
 * it up via `getAll<FeatureEntryProvider>()` and the Timeline bottom-nav root resolves.
 */
val timelineModule = module {
    single {
        TimelineFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
