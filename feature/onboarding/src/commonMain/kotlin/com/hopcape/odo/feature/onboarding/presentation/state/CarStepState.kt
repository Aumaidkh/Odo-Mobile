package com.hopcape.odo.feature.onboarding.presentation.state

import androidx.compose.runtime.Immutable

/**
 * The plate route of the car step: one field the owner types, and one answer the registry
 * gives back.
 *
 * The odometer is not here — it is asked for on both routes, so it lives on
 * [OnboardingUiState] instead. See the note there.
 */
@Immutable
internal data class CarStepState(
    val plate: FormField<String> = FormField(""),
    val lookup: PlateLookup = PlateLookup.Idle,
) {
    /** The resolved car, if the lookup found one — the only place a match comes from. */
    val match: RtoMatch? get() = (lookup as? PlateLookup.Found)?.match

    /**
     * Whether the plate is a registration number rather than a half-typed one. Indian plates
     * run 9–11 characters normalized (`MH12AB1234`, `DL8CAF5031`, `22BH1234AA`), so anything
     * shorter is still being typed.
     *
     * The car step cannot be finished without one on **either** route — see
     * [OnboardingUiState.canContinue]. The plate is what a bill, a reminder, an insurance
     * document and a resale report all identify the car by, and a car saved without one can
     * never be matched to any of them afterwards.
     */
    val isPlateValid: Boolean get() = plate.text.length in MIN_PLATE_LENGTH..MAX_PLATE_LENGTH

    /**
     * Whether the plate is worth a lookup — the same test as [isPlateValid], because a plate
     * short enough to still be typed is one a request would only waste a round trip on.
     */
    val isPlateLookupReady: Boolean get() = isPlateValid

    /**
     * Whether the plate route has named a car. The odometer is the step's, not this route's,
     * so [OnboardingUiState.canContinue] is what combines the two.
     */
    val isAnswered: Boolean get() = match != null

    private companion object {
        const val MIN_PLATE_LENGTH = 9
        const val MAX_PLATE_LENGTH = 11
    }
}
