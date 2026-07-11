package com.hopcape.odo.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/**
 * The app's navigation host — a thin wrapper over Navigation 3's [NavDisplay].
 *
 * It collects [navigationManager]'s commands and applies them to [navigator], and
 * builds the graph by letting every [FeatureEntryProvider] register its screens.
 * Everything here is host-only — features just emit commands and contribute
 * entries; they never see the [navigator] or the back stack.
 *
 * Wire it once in `:app`:
 * ```
 * val navigator = rememberNavigator(OdoDestination.Home)
 * OdoNavHost(navigator, navigationManager, entryProviders = getKoin().getAll())
 * ```
 */
@Composable
fun OdoNavHost(
    navigator: Navigator,
    navigationManager: NavigationManager,
    entryProviders: List<FeatureEntryProvider>,
    modifier: Modifier = Modifier,
) {
    // Drain the command bus into back-stack operations. Restarts if either the
    // navigator or the manager identity changes.
    LaunchedEffect(navigator, navigationManager) {
        navigationManager.commands.collect { command -> navigator.execute(command) }
    }

    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        modifier = modifier,
        // A single, app-wide motion language: pushes slide in from the trailing
        // edge while the current screen eases back; pops (button + predictive
        // gesture) reverse it. Every feature — including bill-scanner — inherits
        // this, so navigation reads as continuous rather than hard cuts.
        transitionSpec = { forwardTransition() },
        popTransitionSpec = { backTransition() },
        predictivePopTransitionSpec = { backTransition() },
        entryProvider = entryProvider {
            entryProviders.forEach { provider -> with(provider) { registerEntries() } }
        },
    )
}

/** Shared duration/easing so forward + back motion feel like one system. */
private const val NAV_TRANSITION_MS = 300

/** Push: new screen slides in from the right + fades; the old one eases left + fades. */
private fun forwardTransition(): ContentTransform =
    (
        slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / 6 } +
            fadeIn(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing))
    ) togetherWith (
        slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 14 } +
            fadeOut(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing))
    )

/** Pop: previous screen eases back in from the left; the current one slides off right. */
private fun backTransition(): ContentTransform =
    (
        slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 14 } +
            fadeIn(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing))
    ) togetherWith (
        slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / 6 } +
            fadeOut(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing))
    )
