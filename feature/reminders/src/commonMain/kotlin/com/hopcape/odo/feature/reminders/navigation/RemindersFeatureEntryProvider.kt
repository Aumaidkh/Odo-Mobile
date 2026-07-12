package com.hopcape.odo.feature.reminders.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.reminders.presentation.RemindersScreen
import com.hopcape.odo.feature.reminders.presentation.sampleRemindersAttention

/**
 * Reminders' contribution to the navigation graph: the top-level
 * [OdoDestination.Reminders] home. Collected by the `:app` host via
 * `getAll<FeatureEntryProvider>()`.
 */
internal class RemindersFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Reminders.List> { RemindersRoute(navigationManager) }
    }
}

/**
 * The reminders route host — renders sample reminders until the reminder engine (renewal
 * triggers + schedules) lands. Manage / open / remind-me / add are M2 stubs.
 */
@Composable
internal fun RemindersRoute(navigationManager: NavigationManager) {
    RemindersScreen(
        state = sampleRemindersAttention(),
        onManage = { /* TODO(M2): open reminder settings. */ },
        onOpen = { /* TODO(M2): open the reminder's source (document / service entry). */ },
        onRemindMe = { /* TODO(M2): opt into a suggested reminder. */ },
        onAdd = { /* TODO(M2): open the add-reminder flow. */ },
    )
}
