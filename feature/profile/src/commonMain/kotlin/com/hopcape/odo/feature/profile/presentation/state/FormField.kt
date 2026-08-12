package com.hopcape.odo.feature.profile.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText

/**
 * One answer in a form, together with whatever is wrong with it.
 *
 * The pairing is the point: a raw value cannot hold the reason it was rejected, so the
 * error ends up somewhere else — a parallel map, a general banner — and the two drift.
 *
 * [update] deliberately drops the error: touching a field means the last verdict on it is
 * stale, and an error that outlives the input it described is worse than none.
 */
@Immutable
internal data class FormField<T>(
    val value: T? = null,
    val error: UiText? = null,
) {
    val isValid: Boolean get() = error == null

    /** The owner answered — take the new value and forget the previous complaint. */
    fun update(value: T?): FormField<T> = FormField(value)

    /** Attach a validation failure, keeping what was answered so it can be corrected. */
    fun fail(message: UiText): FormField<T> = copy(error = message)
}

/** A text field's content, never null — spares every call site an `orEmpty()`. */
internal val FormField<String>.text: String get() = value.orEmpty()
