package com.hopcape.odo.feature.dashboard.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.dashboard.presentation.home.HomeEffect
import com.hopcape.odo.feature.dashboard.presentation.home.HomeScreen
import com.hopcape.odo.feature.dashboard.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Dashboard's contribution to the navigation graph: the **Home** tab
 * ([OdoDestination.Home]) — the dashboard's own cross-feature glance.
 *
 * As an aggregator it never imports a sibling feature; Home jumps to the health-score
 * detail, the vault, the service log, the timeline and the bill scanner through the shared
 * [OdoDestination] keys.
 *
 * The other bottom-nav roots are owned by their features: **Timeline** by
 * `:feature:timeline`, **Costs** by cost-tracker, **Garage** by `:feature:garage`.
 */
internal class DashboardFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Home> { HomeRoute(navigationManager) }
    }
}

/** The Home tab route host. Everything that leaves the screen is a navigation. */
@Composable
internal fun HomeRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            HomeEffect.OpenHealthScore -> navigationManager.navigateTo(OdoDestination.HealthScore.Detail)
            HomeEffect.OpenVault -> navigationManager.navigateTo(OdoDestination.Documents.Vault)
            is HomeEffect.OpenServiceLog ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.List(carId = effect.carId))

            HomeEffect.OpenTimeline -> navigationManager.navigateTo(OdoDestination.Timeline.List)
            is HomeEffect.OpenService ->
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.Detail(logId = effect.logId, carId = effect.carId),
                )

            HomeEffect.OpenReminders -> navigationManager.navigateTo(OdoDestination.Reminders.List)
            HomeEffect.OpenProfile -> navigationManager.navigateTo(OdoDestination.Profile.Root)
            HomeEffect.OpenScanner -> navigationManager.navigateTo(OdoDestination.BillScanner.Capture)
            HomeEffect.OpenAddDocument -> navigationManager.navigateTo(OdoDestination.Documents.Add())
            HomeEffect.OpenAddCar -> navigationManager.navigateTo(OdoDestination.Garage.AddCar)
        }
    }

    HomeScreen(state = state, onEvent = viewModel::onEvent)
}
