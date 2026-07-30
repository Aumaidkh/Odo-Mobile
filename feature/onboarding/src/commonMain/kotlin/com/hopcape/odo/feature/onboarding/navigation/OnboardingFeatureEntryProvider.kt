package com.hopcape.odo.feature.onboarding.navigation

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
import com.hopcape.odo.feature.onboarding.presentation.OnboardingEffect
import com.hopcape.odo.feature.onboarding.presentation.OnboardingFlow
import com.hopcape.odo.feature.onboarding.presentation.OnboardingViewModel
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeEffect
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeScreen
import com.hopcape.odo.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding's contribution to the navigation graph: registers the whole first-run
 * flow — the [OdoDestination.Welcome] pitch and [OdoDestination.Onboarding] car setup.
 * Both belong to this one feature, so they're registered by this one provider (a
 * `registerEntries` block can contribute any number of entries). Collected by the `:app`
 * host (`getAll<FeatureEntryProvider>()`), so no other module references onboarding.
 *
 * The routes below are **only** a state-and-effects bridge: they render a ViewModel's state,
 * forward its events, and translate its effects into navigation commands. Every decision
 * about *what* should happen is made in presentation, which is why the `when` blocks here
 * contain no logic beyond building the key.
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
 * The Welcome route. Continue goes straight into car setup — no sign-in first. Odo is
 * offline-first, so first run has to reach a working car without an account; auth is
 * offered *after* setup, and only if there's no session (see [OnboardingEffect.Finish]).
 */
@Composable
internal fun WelcomeRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<WelcomeViewModel>()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WelcomeEffect.OpenCarSetup -> navigationManager.navigateTo(OdoDestination.Onboarding)
            // TODO(legal): open the hosted Terms / Privacy pages. There is no in-app browser
            //  capability yet, so the taps are collected and deliberately go nowhere rather
            //  than opening a blank screen.
            WelcomeEffect.OpenTerms -> Unit
            WelcomeEffect.OpenPrivacy -> Unit
        }
    }
    WelcomeScreen(onEvent = viewModel::onEvent)
}

/**
 * The setup route — steps 2 to 4 behind one destination, because they are one form: back
 * moves between steps instead of popping screens, and the header's progress stays
 * continuous across them. Keeping them behind one key is also what lets the flow survive
 * the trip out to the Bill Scanner: the entry is never popped, so its ViewModel isn't either.
 */
@Composable
internal fun OnboardingRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.NavigateBack -> navigationManager.back()

            // TODO(billscanner): hand the saved car to BillScanner.Capture and come back —
            //  waiting on the car actually being persisted during setup.
            OnboardingEffect.OpenBillScanner -> navigationManager.navigateTo(OdoDestination.BillScanner.Capture)

            is OnboardingEffect.Finish -> {
                val destination = effect.start.toOdoDestination()
                val next = if (effect.signInFirst) OdoDestination.Auth.Phone(destination) else destination
                // Welcome and the setup steps leave the back stack — first run doesn't repeat.
                navigationManager.navigateTo(next, popUpTo = OdoDestination.Welcome, inclusive = true)
            }
        }
    }

    OnboardingFlow(state = state, onEvent = viewModel::onEvent)
}
