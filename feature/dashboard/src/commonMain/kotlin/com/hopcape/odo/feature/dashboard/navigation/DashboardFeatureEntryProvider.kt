package com.hopcape.odo.feature.dashboard.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.dashboard.presentation.home.HomeScreen
import com.hopcape.odo.feature.dashboard.presentation.home.samplePopulated

/**
 * Dashboard's contribution to the navigation graph: the **Home** tab
 * ([OdoDestination.Home]) — the dashboard's own cross-feature glance.
 *
 * As an aggregator it never imports a sibling feature; Home jumps to the health-score
 * detail, documents, timeline, service-log detail and bill scanner through the shared
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

/**
 * The Home tab route host. Renders sample state until the aggregation use case (reading
 * the `:core:domain` health-score / cost / document / service-log ports) lands; swap
 * `samplePopulated()` ↔ `sampleAllClear()` ↔ `sampleNewUser()` to preview each state.
 */
@Composable
internal fun HomeRoute(navigationManager: NavigationManager) {
    val carId = "sample-car" // TODO(active-car)
    HomeScreen(
        state = samplePopulated(),
        onSeeBreakdown = { navigationManager.navigateTo(OdoDestination.HealthScore.Detail) },
        onOpenAttention = { navigationManager.navigateTo(OdoDestination.Documents.Vault) },
        onTimeline = { navigationManager.navigateTo(OdoDestination.Timeline.List) },
        onOpenRecent = { logId ->
            navigationManager.navigateTo(OdoDestination.ServiceLog.Detail(logId = logId, carId = carId))
        },
        onBell = { navigationManager.navigateTo(OdoDestination.Reminders.List) },
        onProfile = { navigationManager.navigateTo(OdoDestination.Profile.Root) },
        onScanBill = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture) },
        onAddDocuments = { navigationManager.navigateTo(OdoDestination.Documents.Add) },
    )
}
