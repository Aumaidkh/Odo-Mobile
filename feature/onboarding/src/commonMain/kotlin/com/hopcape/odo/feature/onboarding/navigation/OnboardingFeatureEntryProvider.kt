package com.hopcape.odo.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEffect
import com.hopcape.odo.feature.onboarding.presentation.ui.OnboardingScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding's contribution to the navigation graph: registers the
 * [OdoDestination.Onboarding] entry. Registered in the feature's Koin module and
 * collected by the `:app` host (`getAll<FeatureEntryProvider>()`), so no other
 * module references onboarding directly.
 */
internal class OnboardingFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Onboarding> { OnboardingRoute(navigationManager) }
    }
}

/**
 * The onboarding route host — the hook between the ViewModel and navigation.
 *
 * It owns the [OnboardingViewModel] (lifecycle-scoped via Koin), renders the
 * stateless [OnboardingScreen] from its state, and bridges the one-shot
 * [OnboardingEffect.NavigateToStart] to a [NavigationManager] command, popping
 * Onboarding off the back stack so completion can't be navigated back to.
 */
@Composable
internal fun OnboardingRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, navigationManager) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.NavigateToStart ->
                    navigationManager.navigateTo(
                        destination = effect.destination.toOdoDestination(),
                        popUpTo = OdoDestination.Onboarding,
                        inclusive = true,
                    )
            }
        }
    }

    OnboardingScreen(state = state, onEvent = viewModel::onEvent)
}
