package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import kotlinx.datetime.LocalDate

/** One form field's value + its (cleared-on-edit) validation error. */
internal data class FormField<T>(
    val value: T? = null,
    val error: UiText? = null,
) {
    fun update(value: T?): FormField<T> = copy(value = value, error = null)
    fun fail(message: UiText): FormField<T> = copy(error = message)
}

/**
 * Immutable state for the add/edit form. Text fields keep raw strings (parsed at the
 * boundary), so partial input survives a round-trip. Odometer is mandatory (Odo's
 * core number); the rest are optional.
 */
internal data class ServiceLogFormUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val workshop: FormField<String> = FormField(value = ""),
    val date: FormField<LocalDate> = FormField(),
    val odometer: FormField<String> = FormField(value = ""),
    val notes: FormField<String> = FormField(value = ""),
    val amount: FormField<String> = FormField(value = ""),
    val categories: Set<ServiceCategory> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitError: UiText? = null,
    val showDeleteConfirm: Boolean = false,
) {
    fun clearErrors(): ServiceLogFormUiState = copy(
        workshop = workshop.copy(error = null),
        date = date.copy(error = null),
        odometer = odometer.copy(error = null),
        notes = notes.copy(error = null),
        amount = amount.copy(error = null),
        submitError = null,
    )
}
