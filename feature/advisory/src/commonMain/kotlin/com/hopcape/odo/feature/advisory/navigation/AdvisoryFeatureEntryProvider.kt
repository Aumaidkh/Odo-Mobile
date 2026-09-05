package com.hopcape.odo.feature.advisory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.share.rememberTextSharer
import com.hopcape.odo.feature.advisory.resources.Res
import com.hopcape.odo.feature.advisory.resources.adv_check_save_failed
import com.hopcape.odo.feature.advisory.resources.adv_check_saved
import org.jetbrains.compose.resources.stringResource
import com.hopcape.odo.feature.advisory.presentation.CarValueEffect
import com.hopcape.odo.feature.advisory.presentation.CarValueScreen
import com.hopcape.odo.feature.advisory.presentation.CarValueViewModel
import com.hopcape.odo.feature.advisory.presentation.checklist.ChecklistEffect
import com.hopcape.odo.feature.advisory.presentation.checklist.ChecklistScreen
import com.hopcape.odo.feature.advisory.presentation.checklist.ChecklistViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

/** The advisory feature's contribution to the navigation graph. */
internal class AdvisoryFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.CarValue> { CarValueRoute(navigationManager) }
        entry<OdoDestination.ServiceChecklist> { ChecklistRoute(navigationManager, it.entry) }
    }
}

/**
 * The value route.
 *
 * The scanner is pushed on top rather than replacing this screen: the whole argument the
 * owner just read is "scanning moves this number", and they have to be able to come back
 * and watch it move.
 */
@Composable
internal fun CarValueRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<CarValueViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val share = rememberTextSharer()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            CarValueEffect.NavigateBack -> navigationManager.back()
            CarValueEffect.OpenScanner ->
                navigationManager.navigateTo(OdoDestination.BillScanner.Capture())

            is CarValueEffect.Share -> share(effect.text)
        }
    }

    CarValueScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The checklist route.
 *
 * Saving is said in a snackbar rather than by leaving the screen: the owner is standing in a
 * car park about to walk in, and a navigation away from the list they came to read would be
 * the wrong reward for saving it.
 */
@Composable
internal fun ChecklistRoute(navigationManager: NavigationManager, entry: String) {
    val viewModel = koinViewModel<ChecklistViewModel> { parametersOf(entry) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saved = stringResource(Res.string.adv_check_saved)
    val failed = stringResource(Res.string.adv_check_save_failed)

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ChecklistEffect.NavigateBack -> navigationManager.back()
            ChecklistEffect.Saved -> scope.launch { snackbarHostState.showSnackbar(saved) }
            ChecklistEffect.SaveFailed -> scope.launch { snackbarHostState.showSnackbar(failed) }
        }
    }

    ChecklistScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}
