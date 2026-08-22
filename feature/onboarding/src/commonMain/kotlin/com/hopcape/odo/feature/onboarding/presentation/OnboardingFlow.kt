package com.hopcape.odo.feature.onboarding.presentation

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
import com.hopcape.odo.feature.onboarding.presentation.car.CarDetailsStepScreen
import com.hopcape.odo.feature.onboarding.presentation.car.CarStepScreen
import com.hopcape.odo.feature.onboarding.presentation.profile.ProfileStepScreen
import com.hopcape.odo.feature.onboarding.presentation.scan.FirstScanScreen
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.onboarding.presentation.state.sampleCarStep
import com.hopcape.odo.feature.onboarding.presentation.state.sampleProfile

/**
 * Steps 2–4 as one screen that changes its mind: the step (and the car step's manual mode)
 * selects which body renders, and the shared chrome around it never moves.
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
        targetState = state.step to state.manualEntry,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(motion.baseMillis, easing = motion.easeStandard)) togetherWith
                fadeOut(tween(motion.baseMillis / 2))
        },
        label = "onboardingStep",
    ) { (step, manualEntry) ->
        when (step) {
            // Both routes of the car step read the same odometer, and `canContinue` comes from
            // the flow rather than from a slice — one authority on "is this step answered".
            OnboardingStep.CAR -> if (manualEntry) {
                CarDetailsStepScreen(
                    details = state.details,
                    // The same plate the other route holds, not a copy: one registration
                    // number per car, and flipping between the routes must not lose it.
                    plate = state.car.plate,
                    odometer = state.odometer,
                    canContinue = state.canContinue,
                    onEvent = onEvent,
                )
            } else {
                CarStepScreen(
                    car = state.car,
                    odometer = state.odometer,
                    canContinue = state.canContinue,
                    onEvent = onEvent,
                )
            }

            OnboardingStep.PROFILE -> ProfileStepScreen(
                profile = state.profile,
                canContinue = state.canContinue,
                onEvent = onEvent,
            )

            OnboardingStep.FIRST_SCAN -> FirstScanScreen(onEvent = onEvent)
        }
    }
}

@OdoThemePreviews
@Composable
private fun OnboardingFlowCarPreview() = OdoPreview(padded = false) {
    OnboardingFlow(state = OnboardingUiState(car = sampleCarStep()), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun OnboardingFlowProfilePreview() = OdoPreview(padded = false) {
    OnboardingFlow(
        state = OnboardingUiState(step = OnboardingStep.PROFILE, profile = sampleProfile()),
        onEvent = {},
    )
}
