package com.hopcape.odo.feature.timeline.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import org.koin.compose.koinInject
import com.hopcape.odo.feature.timeline.presentation.TimelineScreen
import com.hopcape.odo.feature.timeline.presentation.sampleTimeline
import com.hopcape.odo.feature.timeline.presentation.sheets.TimelineFilterSheetContent

/**
 * Timeline's contribution to the navigation graph — the [OdoDestination.Timeline] group:
 * the tab root + its "show in timeline" filter sheet. As an aggregator, Timeline never
 * imports a sibling feature — an entry's detail reuses [OdoDestination.ServiceLog.Detail],
 * sharing reuses [OdoDestination.ServiceLog.Share], and "scan/add bill" jumps to
 * [OdoDestination.BillScanner.Capture], all via the shared keys.
 */
internal class TimelineFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Timeline.List> { TimelineRoute(navigationManager) }
        entry<OdoDestination.Timeline.Filter>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) {
            TimelineFilterSheetContent(onShow = { navigationManager.back() })
        }
    }
}

/**
 * The Timeline tab route host. Renders sample state until the aggregation use case
 * (reading the `:core:domain` service-log / document / health-score ports) lands; swap
 * `sampleTimeline()` ↔ `sampleEmptyTimeline()` to preview the populated vs new-user state.
 */
@Composable
internal fun TimelineRoute(navigationManager: NavigationManager) {
    // The car this timeline is of, read at tap time; null until setup has stored one.
    val activeCar = koinInject<ActiveCarProvider>()
    TimelineScreen(
        state = sampleTimeline(),
        onFilter = { navigationManager.navigateTo(OdoDestination.Timeline.Filter) },
        onShare = {
            activeCar.activeCarId.value?.let { carId ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Share(carId = carId.value))
            }
        },
        onOpenService = { logId ->
            activeCar.activeCarId.value?.let { carId ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Detail(logId = logId.value, carId = carId.value))
            }
        },
        onAddBill = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture) },
        onScanFirst = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture) },
    )
}
