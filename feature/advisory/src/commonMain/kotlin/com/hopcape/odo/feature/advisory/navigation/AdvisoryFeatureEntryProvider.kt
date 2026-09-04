package com.hopcape.odo.feature.advisory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.hopcape.odo.feature.advisory.presentation.CarValueEffect
import com.hopcape.odo.feature.advisory.presentation.CarValueScreen
import com.hopcape.odo.feature.advisory.presentation.CarValueViewModel
import org.koin.compose.viewmodel.koinViewModel

/** The advisory feature's contribution to the navigation graph. */
internal class AdvisoryFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.CarValue> { CarValueRoute(navigationManager) }
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
