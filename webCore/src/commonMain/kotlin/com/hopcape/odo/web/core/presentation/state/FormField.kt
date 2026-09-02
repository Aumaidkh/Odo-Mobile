package com.hopcape.odo.web.core.presentation.state

import androidx.compose.runtime.Immutable

/**
 * One input, with whatever is currently wrong with it.
 *
 * The error travels with the value so a screen cannot draw a field and its
 * message out of step. [update] clears the error on purpose: the moment someone
 * starts fixing a field, telling them it is still wrong is noise — the next
 * submit will say so again if it still is.
 */
@Immutable
data class FormField<T>(
    val value: T,
    val error: UiText? = null,
) {
    /** A new value, and no complaint about it yet. */
    fun update(value: T): FormField<T> = FormField(value)

    /** Same value, now with a reason it was rejected. */
    fun fail(message: UiText): FormField<T> = copy(error = message)

    val isValid: Boolean get() = error == null
}

/** An empty text field, which is where every form in this module starts. */
fun textField(initial: String = ""): FormField<String> = FormField(initial)
