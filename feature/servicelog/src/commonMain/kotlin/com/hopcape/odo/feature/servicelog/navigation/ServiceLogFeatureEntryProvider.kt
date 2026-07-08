package com.hopcape.odo.feature.servicelog.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider

/**
 * ServiceLog's contribution to the navigation graph. Collected by the `:app` host
 * (`getAll<FeatureEntryProvider>()`), so no other module references servicelog
 * directly.
 *
 * Scaffold only for now — this feature's screens (the
 * [com.hopcape.odo.core.navigation.OdoDestination.ServiceLog] sealed group: `List`,
 * `Detail`, `AddEdit`) get registered here as they land. Every entry needs an
 * odometer reading (Odo's mandatory, first-class field), so the add/edit form is
 * the next slice to build on this.
 */
internal class ServiceLogFeatureEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        // TODO(servicelog): entry<OdoDestination.ServiceLog.List/.Detail/.AddEdit>
        //  { key -> … } once the screens + ViewModels exist.
    }
}
