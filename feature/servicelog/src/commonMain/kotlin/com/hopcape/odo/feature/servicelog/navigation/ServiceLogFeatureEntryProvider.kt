package com.hopcape.odo.feature.servicelog.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider

/**
 * ServiceLog's contribution to the navigation graph. Collected by the `:app` host
 * (`getAll<FeatureEntryProvider>()`), so no other module references servicelog
 * directly.
 *
 * Scaffold only for now — this feature's screens (manual service-log entry keyed by
 * [com.hopcape.odo.core.navigation.OdoDestination.AddServiceLog]) get registered
 * here as they land. Every entry needs an odometer reading (Odo's mandatory,
 * first-class field), so the add-log route is the next slice to build on this.
 */
internal class ServiceLogFeatureEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        // TODO(servicelog): entry<OdoDestination.AddServiceLog> { key -> … } once the
        //  add-service-log screen + ViewModel exist.
    }
}
