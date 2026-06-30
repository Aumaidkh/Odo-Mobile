package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * Immutable render state for the whole onboarding flow. The UI is a pure function
 * of this; it never computes validation, persistence, or routing.
 *
 * Car-detail inputs and their per-field errors live together in [form] (see
 * [CarForm] / [FormField]). Error strings are Hinglish, user-facing messages
 * derived from [com.hopcape.odo.core.domain.shared.DomainError] — the UI just shows them.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CAR_DETAILS,

    // --- Step 1: Car Details inputs + co-located field errors ---
    val form: CarForm = CarForm(),

    // --- Dropdown reference data (from VehicleCatalog; never hardcoded) ---
    val isCatalogLoading: Boolean = true,
    val makes: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val fuelTypes: List<FuelType> = emptyList(),

    // --- Goal + submission ---
    val selectedGoal: OnboardingGoal? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,

    // --- History import ---
    val scanUnavailableMessage: String? = null,
)
