package com.hopcape.odo.feature.onboarding.presentation.ui

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.feature.onboarding.presentation.state.CarForm
import com.hopcape.odo.feature.onboarding.presentation.state.FormField
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState

/**
 * Canned [OnboardingUiState]s for `@Preview`s only — one per step, mirroring the
 * mockups so the previews render the same filled-in flow the designer drew. Kept
 * out of the step files so each stays focused on layout.
 */

private val sampleYears: List<Int> = (2025 downTo 2005).toList()

internal fun previewCarDetailsState(): OnboardingUiState = OnboardingUiState(
    step = OnboardingStep.CAR_DETAILS,
    form = CarForm(
        make = FormField(value = "Maruti Suzuki"),
        model = FormField(value = "Swift"),
        year = FormField(value = 2020),
        fuelType = FormField(value = FuelType.PETROL),
        odometer = FormField(value = "54,000"),
        purchaseYear = FormField(value = 2020),
    ),
    isCatalogLoading = false,
    makes = listOf("Maruti Suzuki", "Hyundai", "Tata"),
    models = listOf("Swift", "Baleno"),
    years = sampleYears,
    fuelTypes = FuelType.entries.toList(),
)

internal fun previewHistoryImportState(): OnboardingUiState =
    previewCarDetailsState().copy(step = OnboardingStep.HISTORY_IMPORT)

internal fun previewGoalSelectionState(): OnboardingUiState = previewCarDetailsState().copy(
    step = OnboardingStep.GOAL_SELECTION,
    selectedGoal = OnboardingGoal.SELL_SOON,
)
