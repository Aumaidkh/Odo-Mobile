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
     * Whether the plate is long enough to be worth a lookup. Indian plates run 9–11
     * characters normalized (`MH12AB1234`, `DL8CAF5031`, `22BH1234AA`), so anything
     * shorter is still being typed and a request would only waste a round trip.
     */
    val isPlateLookupReady: Boolean get() = plate.text.length in MIN_PLATE_LENGTH..MAX_PLATE_LENGTH

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
