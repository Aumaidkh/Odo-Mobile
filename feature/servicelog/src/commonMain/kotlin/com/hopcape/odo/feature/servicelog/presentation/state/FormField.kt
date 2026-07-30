package com.hopcape.odo.feature.servicelog.presentation.state

import com.hopcape.odo.core.designsystem.text.UiText

/**
 * One form field: its current [value] and a (cleared-on-edit) validation [error] as a
 * [UiText] so the message is resource-typed, not a hardcoded string. Generic over the
 * field's value type — text fields are `FormField<String>`, the date `FormField<LocalDate>`.
 *
 * Shared by every form surface in the feature (the add/edit form, the report note), which
 * is why it sits in `state/` rather than inside one screen's file.
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

    /** Drop the error, keeping the input — what a fresh validation pass starts from. */
    fun clearError(): FormField<T> = copy(error = null)
}

/** The field's text, or the empty string — what a text input renders. */
internal val FormField<String>.text: String get() = value.orEmpty()
