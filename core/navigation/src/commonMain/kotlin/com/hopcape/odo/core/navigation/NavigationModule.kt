package com.hopcape.odo.core.navigation

import org.koin.dsl.module

/**
 * Koin wiring for the navigation core — the analog of the inspiration's Hilt
 * `NavigationModule` (`@Binds @Singleton`). Publishes the app-wide
 * [NavigationManager] as a singleton so any feature ViewModel can inject the
 * interface without knowing [DefaultNavigationManager].
 *
 * Each `:feature:*` module additionally registers its [FeatureEntryProvider]
 * (`single { … } bind FeatureEntryProvider::class`); the app collects them with
 * `getKoin().getAll<FeatureEntryProvider>()` and passes them to [OdoNavHost].
 */
val coreNavigationModule = module {
    single<NavigationManager> { NavigationManager() }
}
