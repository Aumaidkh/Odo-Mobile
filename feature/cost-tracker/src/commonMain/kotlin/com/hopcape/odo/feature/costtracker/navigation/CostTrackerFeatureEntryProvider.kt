package com.hopcape.odo.feature.costtracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.FuelRateEffect
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.FuelRateSheetContent
import com.hopcape.odo.feature.costtracker.presentation.fuelrate.FuelRateViewModel
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostEffect
import com.hopcape.odo.feature.costtracker.presentation.runningcost.RunningCostEvent
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
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.CostTracker.Home> { RunningCostRoute(navigationManager) }
        entry<OdoDestination.CostTracker.FuelRate>(
            metadata = ModalBottomSheetSceneStrategy.bottomSheet(),
        ) { FuelRateRoute(navigationManager) }
    }
}

/**
 * The running-cost route host. The ViewModel owns the period and the figures; the only
 * thing that leaves the screen is the request to correct the fuel rate.
 */
@Composable
internal fun RunningCostRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<RunningCostViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leaving while the odometer coach mark is up releases the arbiter's grant without
    // burning the hook's one showing — the owner never answered it (#229).
    DisposableEffect(Unit) {
        onDispose { viewModel.onEvent(RunningCostEvent.OdometerShowcaseLeft) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RunningCostEffect.OpenFuelRate ->
                navigationManager.navigateTo(OdoDestination.CostTracker.FuelRate)
        }
    }

    RunningCostScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The fuel-rate sheet host. Saving or clearing both finish the sheet, so the one effect it
 * has is "close me" — the running-cost screen behind it is already observing the price and
 * rebuilds itself.
 */
@Composable
internal fun FuelRateRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<FuelRateViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            FuelRateEffect.Dismiss -> navigationManager.back()
        }
    }

    FuelRateSheetContent(state = state, onEvent = viewModel::onEvent)
}
