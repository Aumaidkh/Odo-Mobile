package com.hopcape.odo.feature.questionnaire.firstrun.navigation

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
import com.hopcape.odo.core.navigation.finishFlow
import com.hopcape.odo.core.navigation.isFirstRunStep
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingEffect
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingFlow
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The setup flow's contribution to the navigation graph.
 *
 * It registers [OdoDestination.Onboarding] — the key did not move when the flow did, so the
 * pitch in `:feature:onboarding` still navigates to it without knowing which module serves it.
 */
internal class SetupFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Onboarding> { SetupRoute(navigationManager) }
    }
}

/**
 * The setup route — steps 2 to 4 behind one destination, because they are one form: back
 * moves between steps instead of popping screens, and the header's progress stays
 * continuous across them.
 *
 * Every way out of the last step is a finish, including its camera button. The flow does
 * not make a round trip to the scanner and come back, so the entry is popped once and the
 * first run is over.
 */
@Composable
internal fun SetupRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.NavigateBack -> navigationManager.back()

            // TODO(ui): show this in a snackbar. Every step screen scaffolds itself with its
            //  own OdoScreen, so the host state has to be threaded through OnboardingFlow
            //  before there is anywhere to post it. Until then a failed write is visible
            //  only as Continue not advancing — which is honest, but not an explanation.
            is OnboardingEffect.SaveFailed -> Unit

            is OnboardingEffect.Finish -> {
                // Always the dashboard. Onboarding used to pick a surface from the owner's
                // goal, but all three choices resolved to this one, so the choice was never
                // real. The goals are now stored by the questionnaire (#394) and read by
                // whatever wants them.
                val destination = OdoDestination.Home
                // The intro and the setup steps leave the back stack — first run doesn't
                // repeat. finishFlow rather than popUpTo(Welcome), because the flow's root
                // is whichever intro the remote flag chose, and popping up to the wrong
                // one silently left the whole first run under the landing screen (#352).
                if (effect.openScanner) {
                    // The start surface is seeded *under* the scanner rather than replaced by
                    // it. Leaving the scan errand pops its own steps and lands on whatever is
                    // below them, so with the scanner alone on the stack there would be
                    // nothing to land on and the owner would be stuck on the viewfinder.
                    navigationManager.finishFlow(destination, ::isFirstRunStep)
                    val scanner = OdoDestination.BillScanner.Capture()
                    // Sign-in still comes first; auth carries the scanner as its `next`, so
                    // both verifying and skipping arrive at the same viewfinder.
                    navigationManager.navigateTo(
                        if (effect.signInFirst) OdoDestination.Auth.Phone(scanner) else scanner,
                    )
                } else {
                    val next =
                        if (effect.signInFirst) OdoDestination.Auth.Phone(destination) else destination
                    navigationManager.finishFlow(next, ::isFirstRunStep)
                }
            }
        }
    }

    OnboardingFlow(state = state, onEvent = viewModel::onEvent)
}

