package com.hopcape.odo.feature.healthscore.navigation

import androidx.compose.runtime.Composable
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
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreEffect
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreScreen
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreViewModel
import com.hopcape.odo.feature.healthscore.presentation.HowScoreWorksContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * HealthScore's contribution to the navigation graph: the [OdoDestination.HealthScore.Detail]
 * screen and the [OdoDestination.HealthScore.Info] explainer, the latter tagged as a bottom
 * sheet so Nav3 presents it as an overlay destination. Collected by the `:app` host via
 * `getAll<FeatureEntryProvider>()`.
 */
internal class HealthScoreFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.HealthScore.Detail> { HealthScoreRoute(navigationManager) }
        entry<OdoDestination.HealthScore.Info>(
            metadata = ModalBottomSheetSceneStrategy.bottomSheet(),
        ) {
            HowScoreWorksContent(onDismiss = { navigationManager.back() })
        }
    }
}

/**
 * The health-score route host. The ViewModel owns the score and the breakdown; what leaves
 * the screen is the explainer, the paywall, and going back.
 *
 * The paywall is opened with a trigger naming this screen, because which surface sold the
 * subscription is the one thing the paywall's own analytics cannot see.
 */
@Composable
internal fun HealthScoreRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<HealthScoreViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            HealthScoreEffect.GoBack -> navigationManager.back()
            HealthScoreEffect.OpenInfo -> navigationManager.navigateTo(OdoDestination.HealthScore.Info)
            HealthScoreEffect.OpenPaywall ->
                navigationManager.navigateTo(OdoDestination.Paywall.Plans(trigger = PAYWALL_TRIGGER))
        }
    }

    HealthScoreScreen(state = state, onEvent = viewModel::onEvent)
}

/** Where the paywall was opened from. A shipped analytics value — do not reword it. */
private const val PAYWALL_TRIGGER = "HEALTH_BREAKDOWN"
