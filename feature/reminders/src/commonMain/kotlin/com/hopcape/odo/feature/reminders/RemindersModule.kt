package com.hopcape.odo.feature.reminders

import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.feature.reminders.navigation.RemindersFeatureEntryProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * DI graph for the reminders feature. `NavigationManager` comes from
 * `coreNavigationModule`; the `:app` host registers them all. The reminders ViewModel +
 * the renewal/service trigger use cases join here as the engine is built.
 */
val remindersModule = module {
    single {
        RemindersFeatureEntryProvider(navigationManager = get())
    } bind FeatureEntryProvider::class
}
