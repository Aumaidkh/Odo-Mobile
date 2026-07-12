package com.hopcape.odo.feature.healthscore.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreScreen
import com.hopcape.odo.feature.healthscore.presentation.HowScoreWorksContent
import com.hopcape.odo.feature.healthscore.presentation.sampleHealthGood

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
 * The health-score route host — renders a sample score until the rule-based health-score
 * use case (35 maintenance / 30 docs / 20 cost / 15 history) lands. The (i) button navigates
 * to the [OdoDestination.HealthScore.Info] sheet; "Unlock" is the paywall stub.
 */
@Composable
internal fun HealthScoreRoute(navigationManager: NavigationManager) {
    HealthScoreScreen(
        state = sampleHealthGood(),
        onBack = { navigationManager.back() },
        onInfo = { navigationManager.navigateTo(OdoDestination.HealthScore.Info) },
        onUnlock = { /* TODO: open the Pro paywall (Razorpay) — feature:paywall. */ },
    )
}
