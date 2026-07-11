package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import kotlinx.datetime.LocalDate

/**
 * One form field: its current [value] and a (cleared-on-edit) validation [error] as a
 * [UiText] so the message is resource-typed, not a hardcoded string. Generic over the
 * field's value type — text fields are `FormField<String>`, the date `FormField<LocalDate>`.
 */
internal data class FormField<T>(
    val value: T? = null,
    val error: UiText? = null,
) {
    val hasError: Boolean get() = error != null

    /** Accept new input and clear any prior error (errors show only after a failed save). */
    fun update(value: T?): FormField<T> = copy(value = value, error = null)

    /** Attach a validation failure. */
    fun fail(message: UiText): FormField<T> = copy(error = message)
}

/**
 * Add / edit form render state — one screen for both modes ([isEditing]). Shared by
 * both directions (1a & 1b enter the same form).
 *
 * Text fields keep raw [String] input (parsed at the save boundary) so partial input
 * survives recomposition; the date is a typed [LocalDate]. Odometer is mandatory — it's
 * Odo's core number — and its guard (present, non-backwards) surfaces as [FormField.error].
 * [categories] are the "what was done" chips. Overlay/progress flags stay top-level,
 * orthogonal to the field values.
 */
internal data class ServiceLogFormUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val workshop: FormField<String> = FormField(value = ""),
    val date: FormField<LocalDate> = FormField(),
    val odometer: FormField<String> = FormField(value = ""),
    val odometerUnit: OdoDistanceUnit = OdoDistanceUnit.KM,
    val amount: FormField<String> = FormField(value = ""),
    val notes: FormField<String> = FormField(value = ""),
    val categories: Set<ServiceCategory> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitError: UiText? = null,
    val showDeleteConfirm: Boolean = false,
) {
    /** Save is offered once the mandatory odometer has input and no write is in flight. */
    val canSave: Boolean get() = !isSubmitting && (odometer.value?.isNotBlank() == true)

    /** Drop every field error + the form-level error (called before re-validating a save). */
    fun clearErrors(): ServiceLogFormUiState = copy(
        workshop = workshop.copy(error = null),
        date = date.copy(error = null),
        odometer = odometer.copy(error = null),
        amount = amount.copy(error = null),
        notes = notes.copy(error = null),
        submitError = null,
    )
}
