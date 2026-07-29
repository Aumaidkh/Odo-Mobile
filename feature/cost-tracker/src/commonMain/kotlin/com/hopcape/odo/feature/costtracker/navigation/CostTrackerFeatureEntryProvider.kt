package com.hopcape.odo.feature.costtracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostScreen
import com.hopcape.odo.feature.costtracker.presentation.runningcost.sampleRunningCost

/**
 * CostTracker's contribution to the navigation graph: the [OdoDestination.CostTracker]
 * running-cost screen. Collected by the `:app` host via `getAll<FeatureEntryProvider>()`,
 * so wiring the feature in is just listing
 * [com.hopcape.odo.feature.costtracker.costTrackerModule].
 */
internal class CostTrackerFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.CostTracker> { RunningCostRoute(navigationManager) }
    }
}

/**
 * The running-cost route host — renders sample figures with a local period reducer
 * until the cost-tracker ViewModel (aggregating the local ledger per car) lands.
 */
@Composable
internal fun RunningCostRoute(navigationManager: NavigationManager) {
    var state by remember { mutableStateOf(sampleRunningCost()) }
    RunningCostScreen(
        state = state,
        onPeriodChange = { state = sampleRunningCost(period = it) }
    )
}
