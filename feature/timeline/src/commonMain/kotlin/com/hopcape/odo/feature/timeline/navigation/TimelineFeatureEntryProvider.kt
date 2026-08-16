package com.hopcape.odo.feature.timeline.navigation

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
import com.hopcape.odo.feature.timeline.presentation.TimelineEffect
import com.hopcape.odo.feature.timeline.presentation.TimelineEvent
import com.hopcape.odo.feature.timeline.presentation.TimelineScreen
import com.hopcape.odo.feature.timeline.presentation.TimelineViewModel
import com.hopcape.odo.feature.timeline.presentation.sheets.TimelineFilterSheetContent
import com.hopcape.odo.feature.timeline.presentation.sheets.TimelineFilterViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Timeline's contribution to the navigation graph — the [OdoDestination.Timeline] group: the
 * tab root and its "show in timeline" filter sheet. As an aggregator, Timeline never imports
 * a sibling feature — an entry's detail reuses [OdoDestination.ServiceLog.Detail], sharing
 * reuses [OdoDestination.ServiceLog.Share], and "scan/add bill" jumps to
 * [OdoDestination.BillScanner.Capture], all through the shared keys.
 */
internal class TimelineFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Timeline.List> { TimelineRoute(navigationManager) }
        entry<OdoDestination.Timeline.Filter>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) {
            TimelineFilterRoute(navigationManager)
        }
    }
}

/** The Timeline tab route host. Everything that leaves the screen is a navigation. */
@Composable
internal fun TimelineRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<TimelineViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leaving while the record coach mark is up releases the arbiter's grant without
    // burning the hook's one showing — the owner never answered it (#233).
    DisposableEffect(Unit) {
        onDispose { viewModel.onEvent(TimelineEvent.RecordShowcaseLeft) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            TimelineEffect.OpenFilter -> navigationManager.navigateTo(OdoDestination.Timeline.Filter)
            is TimelineEffect.ShareRecord ->
                navigationManager.navigateTo(OdoDestination.ServiceLog.Share(carId = effect.carId))

            is TimelineEffect.OpenService ->
                navigationManager.navigateTo(
                    OdoDestination.ServiceLog.Detail(logId = effect.logId, carId = effect.carId),
                )

            TimelineEffect.OpenScanner -> navigationManager.navigateTo(OdoDestination.BillScanner.Capture())
        }
    }

    TimelineScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The filter sheet's route host.
 *
 * Its own ViewModel over the shared filter store, so a toggle narrows the feed behind the
 * sheet immediately and the button has nothing left to do but dismiss.
 */
@Composable
internal fun TimelineFilterRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<TimelineFilterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    TimelineFilterSheetContent(
        state = state,
        onCategory = viewModel::onCategoryToggled,
        onOnlyFlagged = viewModel::onOnlyFlaggedToggled,
        onShow = { navigationManager.back() },
    )
}
