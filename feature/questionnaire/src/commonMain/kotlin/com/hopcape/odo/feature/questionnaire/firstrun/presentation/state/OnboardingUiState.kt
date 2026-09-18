package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import androidx.compose.runtime.Immutable

/**
 * Everything the first-run flow renders. One state for the whole flow, because steps 2–4
 * sit behind a single destination: back moves between steps instead of popping screens, and
 * the header's progress stays continuous across them.
 *
 * It is a **composition of per-step slices** rather than one flat bag of fields. Each screen
 * is handed only its own slice, so the profile step cannot read the car catalog and a new
 * field on one step can't quietly change what another renders.
 */
@Immutable
internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CAR,
    val details: CarDetailsState = CarDetailsState(),
    /**
     * The car's current reading.
     *
     * At the flow level rather than on the car step because more than one step reads it, and
     * two fields that have to be kept equal is a drift waiting to happen.
     */
    val odometer: FormField<Long> = FormField(),
    val profile: ProfileState = ProfileState(),
    val workshop: WorkshopState = WorkshopState(),
    val lastService: LastServiceState = LastServiceState(),
) {
    /**
     * Continue enabled for the current step; the last step is always skippable.
     *
     * The single authority on "is this step answered" — the slices each answer only for
     * their own fields.
     *
     * The plate is **not** part of it. Setup used to gate step 1 of 4 on a registration
     * number, which is the most guarded thing an owner can be asked for and the first thing
     * they were asked; it is now taken later, where the feature needing it says why.
     *
     * Nor is the odometer. It is often not in the owner's head — they are not at the car, or
     * do not remember — and a car saved without one is stored pending and asked again, on
     * Home and at every feature that cannot work without it.
     */
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.CAR -> details.isAnswered
            OnboardingStep.PROFILE -> profile.isAnswered
            OnboardingStep.WORKSHOP -> workshop.isAnswered
            // Done is always live on the last step: "don't remember" is an answer, and an
            // owner who wants neither has Skip beside it.
            OnboardingStep.LAST_SERVICE -> true
        }
}
