package com.hopcape.odo.feature.costtracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostScreen
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * CostTracker's contribution to the navigation graph: the [OdoDestination.CostTracker]
 * running-cost screen. Collected by the `:app` host via `getAll<FeatureEntryProvider>()`,
 * so wiring the feature in is just listing
 * [com.hopcape.odo.feature.costtracker.costTrackerModule].
 */
internal class CostTrackerFeatureEntryProvider(
    @Suppress("unused") private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.CostTracker> { RunningCostRoute() }
    }
}

/**
 * The running-cost route host. The screen only reads, so there is nothing to collect and
 * nowhere to navigate — the ViewModel owns the period and the figures.
 */
@Composable
internal fun RunningCostRoute() {
    val viewModel = koinViewModel<RunningCostViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    RunningCostScreen(state = state, onEvent = viewModel::onEvent)
}
