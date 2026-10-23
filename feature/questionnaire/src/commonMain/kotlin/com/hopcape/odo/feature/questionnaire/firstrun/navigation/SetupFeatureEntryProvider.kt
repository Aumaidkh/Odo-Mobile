package com.hopcape.odo.feature.questionnaire.firstrun.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.FirstRunVariant
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2.SetupV2Effect
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2.SetupV2Screen
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2.SetupV2ViewModel
import org.koin.core.parameter.parametersOf
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
        entry<OdoDestination.Onboarding> { key ->
            // v2 always arrives with the Welcome tile's make; anything else is the v1 form.
            val make = key.make
            if (key.variant == FirstRunVariant.V2 && make != null) {
                SetupV2Route(navigationManager, make)
            } else {
                SetupRoute(navigationManager)
            }
        }
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
    val snackbarHostState = remember { SnackbarHostState() }
    // Held rather than shown on the spot: the effect lambda is neither composable nor
    // suspending, and the message is a UiText that only a composition can resolve.
    var failure by remember { mutableStateOf<UiText?>(null) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.NavigateBack -> navigationManager.back()

            // A refused step keeps the owner where they are, so this message is the only
            // thing that separates "the write failed" from a dead button.
            is OnboardingEffect.SaveFailed -> failure = effect.message

            OnboardingEffect.ShowValue -> {
                // A step of first run, not a screen after it: the flow stays open so the
                // progress bar carries on and back returns to the car step. Setup has just
                // taken four answers and given nothing back, and this is the one screen that
                // can pay for them immediately — the gap it shows is the argument for the
                // scan that follows.
                navigationManager.navigateTo(OdoDestination.CarValue(firstRun = true))
            }
        }
    }

    val failureText = failure?.asString()
    LaunchedEffect(failureText) {
        if (failureText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(failureText)
        // Cleared so the same message shows again the next time a save is refused.
        failure = null
    }

    // The flow's steps share one plain Column rather than a Scaffold, so the host lives here
    // over all of them instead of being threaded through each screen.
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingFlow(state = state, onEvent = viewModel::onEvent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Follows the keyboard the way the step's own CTA does, so a refusal is not
                // posted underneath it.
                .imePadding(),
        )
    }
}


/** The v2 setup route. The value screen is pushed on top, so back returns to Year and fuel. */
@Composable
internal fun SetupV2Route(navigationManager: NavigationManager, make: String) {
    val viewModel = koinViewModel<SetupV2ViewModel>(key = "setup-v2-$make") { parametersOf(make) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<UiText?>(null) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SetupV2Effect.NavigateBack -> navigationManager.back()
            is SetupV2Effect.SaveFailed -> failure = effect.message
            SetupV2Effect.ShowValue -> navigationManager.navigateTo(OdoDestination.CarValue(firstRun = true))
        }
    }

    val failureText = failure?.asString()
    LaunchedEffect(failureText) {
        if (failureText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(failureText)
        failure = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SetupV2Screen(state = state, onEvent = viewModel::onEvent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}
