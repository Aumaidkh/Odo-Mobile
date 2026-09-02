package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingUiState

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

    /** Step 2, plate route. */
    sealed interface Car : OnboardingEvent {
        /** The plate field changed; [plate] is already normalized by the input component. */
        data class PlateChanged(val plate: String) : Car

        /** "Try again" on a failed lookup — same plate, fresh attempt. */
        data object LookupRetried : Car

        /** "Not your car?" — the match (or the miss) is rejected in favour of manual entry. */
        data object MatchRejected : Car
    }

    /** Step 2, manual route. */
    sealed interface Details : OnboardingEvent {
        data class MakeSelected(val make: String) : Details
        data class ModelSelected(val model: CarModel) : Details
        data class YearSelected(val year: Int) : Details
        data class FuelSelected(val fuel: FuelType) : Details

        /** The reference data failed to load and the owner asked for another attempt. */
        data object CatalogRetried : Details

        /** "Found your plate instead?" — back to the plate route. */
        data object TryAutoFillClicked : Details
    }

    /** Step 3. */
    sealed interface Profile : OnboardingEvent {
        data class NameChanged(val name: String) : Profile
        /** [value] is a stored constant name from the registry, e.g. `TRACK_COSTS`. */
        data class GoalToggled(val value: String) : Profile
    }

    /** Step 4. Its primary action is the scan, so it does not use [ContinueClicked]. */
    sealed interface Scan : OnboardingEvent {
        data object ScanClicked : Scan
        data object SkipClicked : Scan
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
     * Setup is over. [start] is the surface the owner's goal earned them, and
     * [signInFirst] is true when there is no session yet — the one point where signing in
     * is offered, because by now there is something concrete worth backing up.
     *
     * [openScanner] is true when the owner left through the first-scan step's camera
     * button rather than by skipping. The scan is *not* an escape from the end of setup:
     * it still finishes here, so the sign-in offer is made first and the scanner opens on
     * top of the dashboard afterwards. Handing off to the scanner directly is what let an owner
     * reach the fairness report — and the profile editor behind its "set your city" —
     * without ever being asked to sign in.
     */
    data class Finish(
        val signInFirst: Boolean,
        val openScanner: Boolean = false,
    ) : OnboardingEffect
}
