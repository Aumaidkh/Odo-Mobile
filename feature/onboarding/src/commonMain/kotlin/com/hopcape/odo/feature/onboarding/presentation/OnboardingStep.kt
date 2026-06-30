package com.hopcape.odo.feature.onboarding.presentation

/**
 * The three sequential screens of the onboarding flow (PRD §5.1). Held as data in
 * [OnboardingUiState] so the UI is a pure function of state and the flow stays
 * unit-testable without Compose.
 */
internal enum class OnboardingStep {
    CAR_DETAILS,
    HISTORY_IMPORT,
    GOAL_SELECTION,
}
