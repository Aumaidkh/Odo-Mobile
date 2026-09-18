package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.car.CarDetailsStepScreen
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingUiState

/**
 * The setup steps as one screen that changes its mind: the step selects which body renders,
 * and the shared chrome around it never moves.
 *
 * Each step is handed **only its own slice** of [state] plus the one `onEvent` sink, so no
 * step can read another's fields and none of them can navigate.
 */
@Composable
internal fun OnboardingFlow(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read outside the spec: transitionSpec isn't composable, and the theme's motion
    // tokens are.
    val motion = OdoTheme.motion
    AnimatedContent(
        targetState = state.step,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(motion.baseMillis, easing = motion.easeStandard)) togetherWith
                fadeOut(tween(motion.baseMillis / 2))
        },
        label = "onboardingStep",
    ) { step ->
        when (step) {
            // `canContinue` comes from the flow rather than from a slice — one authority on
            // "is this step answered".
            OnboardingStep.CAR -> CarDetailsStepScreen(
                details = state.details,
                odometer = state.odometer,
                canContinue = state.canContinue,
                onEvent = onEvent,
            )



        }
    }
}

@OdoThemePreviews
@Composable
private fun OnboardingFlowCarPreview() = OdoPreview(padded = false) {
    OnboardingFlow(state = OnboardingUiState(), onEvent = {})
}

