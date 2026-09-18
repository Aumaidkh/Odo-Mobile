package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingUiState
import kotlinx.datetime.LocalDate

/**
 * What the owner did, as data. One hierarchy for the whole flow (it is one ViewModel), but
 * **nested per step** rather than flat: a step's screen takes `(slice, onEvent)` and can
 * only sensibly emit its own group, the ViewModel dispatches on the group instead of a
 * twenty-branch `when`, and adding a field to one step leaves the others' contracts alone.
 *
 * [ContinueClicked] / [BackClicked] sit at the top level because the shared step chrome
 * renders them, not any one step's body.
 */
internal sealed interface OnboardingEvent {

    /** Step 1 — the car. */
    sealed interface Details : OnboardingEvent {
        data class MakeSelected(val make: String) : Details
        data class ModelSelected(val model: CarModel) : Details
        data class YearSelected(val year: Int) : Details
        data class FuelSelected(val fuel: FuelType) : Details

        /** The reference data failed to load and the owner asked for another attempt. */
        data object CatalogRetried : Details
    }

    /**
     * The odometer changed. Not nested under a route: both routes of the car step ask for it
     * and it is the same reading either way (see [OnboardingUiState.odometer]).
     */
    data class OdometerChanged(val km: Long) : OnboardingEvent


    /** The shared chrome's primary action — advances a step, or finishes on the last one. */
    data object ContinueClicked : OnboardingEvent

    /** The shared chrome's back button — rewinds a step, or leaves the flow at the first. */
    data object BackClicked : OnboardingEvent
}

/**
 * One-shot things that happen *outside* the flow's own state — collected once by the route
 * host, never re-applied on recomposition.
 *
 * Every one of them is data: the ViewModel decides *what* should happen and the route turns
 * it into a navigation command. That is why [Finish] carries flags rather than a `NavKey` —
 * presentation stays free of navigation types.
 */
internal sealed interface OnboardingEffect {

    /** Back from the first step: leave onboarding entirely (pop to the Welcome pitch). */
    data object NavigateBack : OnboardingEffect

    /**
     * A step's answers could not be stored, for a reason no single field owns (the local
     * write failed). The flow stays where it is; this is only how the owner is told why
     * Continue did nothing.
     */
    data class SaveFailed(val message: UiText) : OnboardingEffect

    /**
     * The car is named. First run carries on to the value screen, which is step 3 of it —
     * the one screen that can pay for the answers setup has just taken.
     */
    data object ShowValue : OnboardingEffect
}
