package com.hopcape.odo.feature.refuel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.FuelFillDraftInput
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.permission.PlatformPermission
import com.hopcape.odo.core.platform.permission.rememberPermissionController
import com.hopcape.odo.feature.refuel.presentation.autodetect.AutoDetectEffect
import com.hopcape.odo.feature.refuel.presentation.autodetect.AutoDetectEvent
import com.hopcape.odo.feature.refuel.presentation.autodetect.AutoDetectScreen
import com.hopcape.odo.feature.refuel.presentation.autodetect.AutoDetectViewModel
import com.hopcape.odo.feature.refuel.presentation.confirm.RefuelConfirmEffect
import com.hopcape.odo.feature.refuel.presentation.confirm.RefuelConfirmSheetContent
import com.hopcape.odo.feature.refuel.presentation.confirm.RefuelConfirmViewModel
import com.hopcape.odo.feature.refuel.presentation.log.RefuelLogEffect
import com.hopcape.odo.feature.refuel.presentation.log.RefuelLogScreen
import com.hopcape.odo.feature.refuel.presentation.log.RefuelLogViewModel
import com.hopcape.odo.feature.refuel.presentation.logged.RefuelLoggedEffect
import com.hopcape.odo.feature.refuel.presentation.logged.RefuelLoggedScreen
import com.hopcape.odo.feature.refuel.presentation.logged.RefuelLoggedViewModel
import com.hopcape.odo.feature.refuel.presentation.pending.PendingFillsEffect
import com.hopcape.odo.feature.refuel.presentation.pending.PendingFillsSheetContent
import com.hopcape.odo.feature.refuel.presentation.pending.PendingFillsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Refuel's contribution to the navigation graph.
 *
 * Four entries, and the shape of them is the feature's whole argument: the capture routes —
 * this feature's form, the scanner's pump mode, the detection notification — all converge on
 * one [OdoDestination.Refuel.Confirm], which is the only place a fill is ever written.
 *
 * Confirm is a bottom sheet because it is a step in something the owner is already doing, not
 * a place they navigated to. Logged is a full screen because it is where the flow ends.
 */
internal class RefuelFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Refuel.Log> { RefuelLogRoute(navigationManager) }
        entry<OdoDestination.Refuel.Confirm>(
            metadata = ModalBottomSheetSceneStrategy.bottomSheet(),
        ) { key -> RefuelConfirmRoute(navigationManager, key.draft) }
        entry<OdoDestination.Refuel.Logged> { key ->
            RefuelLoggedRoute(navigationManager, key.fillId)
        }
        entry<OdoDestination.Refuel.AutoDetect> { AutoDetectRoute(navigationManager) }
        entry<OdoDestination.Refuel.Pending>(
            metadata = ModalBottomSheetSceneStrategy.bottomSheet(),
        ) { PendingFillsRoute(navigationManager) }
    }
}

@Composable
internal fun RefuelLogRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<RefuelLogViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is RefuelLogEffect.Confirm ->
                navigationManager.navigateTo(OdoDestination.Refuel.Confirm(effect.draft))

            RefuelLogEffect.OpenPumpScanner -> navigationManager.navigateTo(
                OdoDestination.BillScanner.Capture(target = ScanTarget.PumpDisplay),
            )
        }
    }

    RefuelLogScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { navigationManager.back() },
    )
}

/**
 * The confirm sheet's host.
 *
 * The draft is passed to the ViewModel as a Koin parameter rather than read from a saved
 * state handle, because it arrives on the navigation key and the key is what owns it.
 *
 * On success the sheet is closed *before* the success screen opens. Leaving it on the stack
 * would put a bottom sheet behind a full screen, and backing out of the success screen would
 * land the owner on a confirm step for a fill that has already been written.
 */
@Composable
internal fun RefuelConfirmRoute(
    navigationManager: NavigationManager,
    draft: FuelFillDraftInput,
) {
    val viewModel = koinViewModel<RefuelConfirmViewModel> { parametersOf(draft) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RefuelConfirmEffect.OpenFuelRate ->
                navigationManager.navigateTo(OdoDestination.CostTracker.FuelRate)

            is RefuelConfirmEffect.Logged -> {
                navigationManager.back()
                navigationManager.navigateTo(OdoDestination.Refuel.Logged(effect.fillId))
            }

            RefuelConfirmEffect.Dismiss -> navigationManager.back()
        }
    }

    RefuelConfirmSheetContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
internal fun RefuelLoggedRoute(navigationManager: NavigationManager, fillId: String) {
    val viewModel = koinViewModel<RefuelLoggedViewModel> { parametersOf(fillId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RefuelLoggedEffect.Close -> navigationManager.back()
            RefuelLoggedEffect.OpenTimeline ->
                navigationManager.navigateTo(OdoDestination.Timeline.List)
        }
    }

    RefuelLoggedScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The unanswered-detections sheet.
 *
 * Reviewing one closes this sheet before opening the confirm step, so backing out of the
 * confirm lands on whatever the owner was doing rather than on a list they have already
 * moved past. The row stays unanswered either way, so nothing is lost by that.
 */
@Composable
internal fun PendingFillsRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<PendingFillsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PendingFillsEffect.Review -> {
                navigationManager.back()
                navigationManager.navigateTo(OdoDestination.Refuel.Confirm(effect.draft))
            }

            PendingFillsEffect.Dismiss -> navigationManager.back()
        }
    }

    PendingFillsSheetContent(state = state, onEvent = viewModel::onEvent)
}

/**
 * The auto-detect opt-in's host, and the only place its two permissions can be driven from.
 *
 * `POST_NOTIFICATIONS` needs a controller, which is a composable because asking needs the
 * Activity — so the status is read here and folded into the ViewModel, and the primary
 * button's tap is performed here against the step the state says it is on.
 *
 * Notification access has no dialog at all: it is a system page the owner walks to and comes
 * back from, and nothing tells this screen that happened. The resume observer is what re-reads
 * both permissions, and it is what keeps the screen from claiming detection is on while the
 * owner has just revoked it.
 */
@Composable
internal fun AutoDetectRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<AutoDetectViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val notifications = rememberPermissionController(PlatformPermission.POST_NOTIFICATIONS)
    LaunchedEffect(notifications.status) {
        viewModel.onEvent(AutoDetectEvent.NotifyStatusObserved(notifications.status))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onEvent(AutoDetectEvent.Resumed)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AutoDetectEffect.Dismiss -> navigationManager.back()

            // The one ask that cannot be performed from the ViewModel: requesting a runtime
            // permission needs the Activity, and only a composable can reach it.
            is AutoDetectEffect.RequestNotifyPermission -> if (effect.blocked) {
                notifications.openAppSettings()
            } else {
                notifications.request()
            }
        }
    }

    AutoDetectScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { navigationManager.back() },
    )
}
