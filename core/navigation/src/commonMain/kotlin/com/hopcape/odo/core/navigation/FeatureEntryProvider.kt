package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * A feature's contribution to the navigation graph — the Navigation 3 analog of a
 * per-feature `NavGraphProvider`. Each feature implements one, registering its
 * screens with the Nav3 `entry<RouteType> { … }` DSL:
 *
 * ```
 * class GarageEntryProvider : FeatureEntryProvider {
 *     override fun EntryProviderScope<NavKey>.registerEntries() {
 *         entry<OdoDestination.Garage.Home> { GarageScreen() }
 *         entry<OdoDestination.CarDetail> { key -> CarDetailScreen(key.carId) }
 *     }
 * }
 * ```
 *
 * The `:app` module collects every implementation (Koin `getAll<FeatureEntryProvider>()`
 * — the analog of Hilt `@IntoSet` multibinding) and hands the list to [OdoNavHost],
 * which applies them all. Adding a feature is then: implement this + register it in
 * the feature's Koin module. No feature references another.
 */
interface FeatureEntryProvider {
    fun EntryProviderScope<NavKey>.registerEntries()
}
