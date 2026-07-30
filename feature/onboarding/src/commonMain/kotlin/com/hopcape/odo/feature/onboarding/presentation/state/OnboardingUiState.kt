package com.hopcape.odo.feature.onboarding.presentation.state

import androidx.compose.runtime.Immutable

/**
 * Everything the first-run flow renders. One state for the whole flow, because steps 2–4
 * sit behind a single destination: back moves between steps instead of popping screens, and
 * the header's progress stays continuous across them.
 *
 * It is a **composition of per-step slices** rather than one flat bag of fields. Each screen
 * is handed only its own slice, so the profile step cannot read the car catalog and a new
 * field on one step can't quietly change what another renders.
 *
 * [manualEntry] is a mode of the car step, not a step of its own — the owner can flip
 * between the plate route and answering by hand without losing their place in the flow.
 */
@Immutable
internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CAR,
    val manualEntry: Boolean = false,
    val car: CarStepState = CarStepState(),
    val details: CarDetailsState = CarDetailsState(),
    /**
     * The car's current reading, asked for on **both** routes of the car step and so held
     * here rather than on either one.
     *
     * There is one odometer for the car being set up regardless of how it got named, and
     * flipping between the plate and the manual form must not lose it — two fields that have
     * to be kept equal is just a drift waiting to happen. It sits at the flow level rather
     * than on a step because Odo cannot compute ₹/km, the health score, or a km anomaly
     * without it, so it is never optional.
     */
    val odometer: FormField<Long> = FormField(),
    val profile: ProfileState = ProfileState(),
) {
    /**
     * Continue enabled for the current step; the last step is always skippable.
     *
     * The single authority on "is this step answered" — the slices each answer only for
     * their own fields, and the car step's answer is whichever route is showing *plus* the
     * odometer they share.
     */
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.CAR ->
                odometer.value != null && if (manualEntry) details.isAnswered else car.isAnswered

            OnboardingStep.PROFILE -> profile.isAnswered
            OnboardingStep.FIRST_SCAN -> true
        }
}
