package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * User intents the UI dispatches to [OnboardingViewModel]. State-in / events-out:
 * the UI computes nothing — it only reports what the user did.
 */
sealed interface OnboardingEvent {

    // --- Step 1: Car Details field changes ---
    data class MakeChanged(val value: String) : OnboardingEvent
    data class ModelChanged(val value: String) : OnboardingEvent
    data class YearChanged(val value: Int?) : OnboardingEvent
    data class FuelTypeChanged(val value: FuelType) : OnboardingEvent
    data class OdometerChanged(val value: String) : OnboardingEvent
    data class PurchaseYearChanged(val value: Int?) : OnboardingEvent
    data class VariantChanged(val value: String) : OnboardingEvent
    data class RegistrationChanged(val value: String) : OnboardingEvent
    data class NicknameChanged(val value: String) : OnboardingEvent

    /** Advance from Car Details — triggers domain validation. */
    data object CarDetailsNext : OnboardingEvent

    /** Go back one step. */
    data object Back : OnboardingEvent

    // --- Step 2: History Import ---
    /** Attempt to scan a bill (stubbed in M1). */
    data object ScanHistoryClicked : OnboardingEvent

    /** Skip history import — always available. */
    data object SkipHistory : OnboardingEvent

    // --- Step 3: Goal Selection (selecting a goal submits the flow) ---
    data class GoalSelected(val goal: OnboardingGoal) : OnboardingEvent
}
