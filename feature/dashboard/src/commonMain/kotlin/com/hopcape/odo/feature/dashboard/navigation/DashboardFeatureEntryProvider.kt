package com.hopcape.odo.feature.dashboard.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.hopcape.odo.feature.dashboard.presentation.home.HomeEvent
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

    // Home leaving composition (tab switch, the trip-logged redirect) while the SCAN
    // coach mark is up must release the arbiter's grant without burning the hook's one
    // showing — the owner never answered it (#228).
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onEvent(HomeEvent.ScanShowcaseLeft)
            viewModel.onEvent(HomeEvent.HealthShowcaseLeft)
        }
    }

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
            HomeEffect.OpenScanner -> navigationManager.navigateTo(OdoDestination.BillScanner.Capture())
            HomeEffect.OpenLogFill -> navigationManager.navigateTo(OdoDestination.Refuel.Log)
            HomeEffect.OpenAutoDetect -> navigationManager.navigateTo(OdoDestination.Refuel.AutoDetect)
            HomeEffect.OpenAutoOdometer -> navigationManager.navigateTo(OdoDestination.AutoOdometer.Education())
            HomeEffect.OpenAddDocument -> navigationManager.navigateTo(OdoDestination.Documents.Add())
            is HomeEffect.OpenServiceChecklist ->
                navigationManager.navigateTo(OdoDestination.ServiceChecklist(entry = effect.entry))

            HomeEffect.OpenAddCar -> navigationManager.navigateTo(OdoDestination.Garage.AddCar)
            HomeEffect.OpenPaywall ->
                navigationManager.navigateTo(OdoDestination.Paywall.Plans(trigger = PAYWALL_TRIGGER_REFUEL))
        }
    }

    HomeScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * Must match a `PaywallTrigger` entry by name — an unrecognised string silently becomes the
 * generic framing, which is how two other entry points in this app already lost theirs.
 */
private const val PAYWALL_TRIGGER_REFUEL = "SMART_REFUEL"
