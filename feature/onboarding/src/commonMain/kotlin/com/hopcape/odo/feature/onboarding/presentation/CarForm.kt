package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.usecase.AddCarCommand

/**
 * A single form field: the user's input value and its validation error kept
 * together, so the two can never drift (editing the value always clears the
 * stale error; an error always belongs to a specific field).
 */
internal data class FormField<T>(
    val value: T? = null,
    val error: String? = null,
) {
    /** Set a new value; editing clears any stale validation error. */
    fun update(value: T?): FormField<T> = copy(value = value, error = null)

    /** Attach a validation error without touching the value. */
    fun fail(message: String): FormField<T> = copy(error = message)
}

/**
 * Car-details inputs for Step 1, with each field's validation error co-located.
 * Domain owns the rules; this maps to [AddCarCommand] at the boundary.
 *
 * `odometer` is raw text (the mandatory field, parsed to Int at the boundary) so
 * partial/invalid input round-trips without losing what the user typed.
 */
internal data class CarForm(
    val make: FormField<String> = FormField(),
    val model: FormField<String> = FormField(),
    val year: FormField<Int> = FormField(),
    val fuelType: FormField<FuelType> = FormField(),
    val odometer: FormField<String> = FormField(value = ""),
    val purchaseYear: FormField<Int> = FormField(),
    val variant: FormField<String> = FormField(value = ""),
    val registrationNumber: FormField<String> = FormField(value = ""),
    val nickname: FormField<String> = FormField(value = ""),
) {
    /** True when any validated field carries an error. */
    val hasErrors: Boolean
        get() = make.error != null || model.error != null || year.error != null ||
            fuelType.error != null || odometer.error != null || purchaseYear.error != null

    /** Clear every field's validation error. */
    fun clearErrors(): CarForm = copy(
        make = make.copy(error = null),
        model = model.copy(error = null),
        year = year.copy(error = null),
        fuelType = fuelType.copy(error = null),
        odometer = odometer.copy(error = null),
        purchaseYear = purchaseYear.copy(error = null),
    )

    /** Map the raw inputs to the domain command (validation happens in the domain). */
    fun toCommand(): AddCarCommand = AddCarCommand(
        make = make.value,
        model = model.value,
        year = year.value,
        fuelType = fuelType.value,
        odometerKm = odometer.value?.trim()?.toIntOrNull(),
        variant = variant.value?.ifBlank { null },
        registrationNumber = registrationNumber.value?.ifBlank { null },
        purchaseYear = purchaseYear.value,
        nickname = nickname.value?.ifBlank { null },
        isPrimary = true,
    )
}
