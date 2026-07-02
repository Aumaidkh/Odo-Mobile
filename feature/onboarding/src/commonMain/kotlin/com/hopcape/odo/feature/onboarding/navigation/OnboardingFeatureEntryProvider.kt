package com.hopcape.odo.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEffect
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.ui.OnboardingScreen
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeEffect
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeScreen
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding's contribution to the navigation graph: registers the whole first-run
 * flow — the [OdoDestination.Welcome] intro carousel and [OdoDestination.Onboarding]
 * car setup. Both belong to this one feature, so they're registered by this one
 * provider (a `registerEntries` block can contribute any number of entries).
 * Collected by the `:app` host (`getAll<FeatureEntryProvider>()`), so no other
 * module references onboarding directly.
 */
internal class OnboardingFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Welcome> { WelcomeRoute(navigationManager) }
        entry<OdoDestination.Onboarding> { OnboardingRoute(navigationManager) }
    }
}

/**
 * The intro-carousel route host — the hook between [WelcomeViewModel] and navigation.
 * Renders the stateless [WelcomeScreen] and bridges the one-shot
 * [WelcomeEffect.NavigateToOnboarding] into car setup. Welcome is deliberately left
 * on the back stack (no pop) so pressing back on the first setup step returns here;
 * onboarding clears it once setup completes.
 */
@Composable
internal fun WelcomeRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<WelcomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, navigationManager) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WelcomeEffect.NavigateToOnboarding ->
                    navigationManager.navigateTo(OdoDestination.Onboarding)
            }
        }
    }

    WelcomeScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * The onboarding route host — the hook between the ViewModel and navigation.
 *
 * It owns the [OnboardingViewModel] (lifecycle-scoped via Koin), renders the
 * stateless [OnboardingScreen] from its state, and bridges its one-shot effects:
 * [OnboardingEffect.NavigateToStart] clears the whole first-run flow (pops up to
 * [OdoDestination.Welcome] inclusive) so completion can't be navigated back to, and
 * [OnboardingEffect.NavigateBack] pops onboarding to return to the intro carousel.
 *
 * System back is routed through the same [OnboardingEvent.Back] as the in-screen
 * back button, so from a later step it rewinds a step and from the first step it
 * exits to Welcome — consistently, whichever affordance the user uses.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OnboardingRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = true) { viewModel.onEvent(OnboardingEvent.Back) }

    LaunchedEffect(viewModel, navigationManager) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.NavigateToStart ->
                    navigationManager.navigateTo(
                        destination = effect.destination.toOdoDestination(),
                        popUpTo = OdoDestination.Welcome,
                        inclusive = true,
                    )
                OnboardingEffect.NavigateBack -> navigationManager.back()
            }
        }
    }

    OnboardingScreen(state = state, onEvent = viewModel::onEvent)
}
