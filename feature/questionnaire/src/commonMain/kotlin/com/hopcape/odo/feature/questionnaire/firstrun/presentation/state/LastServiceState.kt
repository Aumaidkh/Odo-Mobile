package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * The owner's last service, as far as they remember it.
 *
 * [forgot] is a first-class answer rather than an empty form, and ticking it clears both
 * fields: a date left behind an unticked box is how a half-row gets written on Done.
 */
@Immutable
internal data class LastServiceState(
    val date: FormField<LocalDate> = FormField(),
    val odometer: FormField<Long> = FormField(),
    val forgot: Boolean = false,
) {
    /** Both fields, or neither. A service with a date and no reading is not worth a row. */
    val isAnswered: Boolean get() = !forgot && date.value != null && odometer.value != null

    /** Fields are editable until the owner says they cannot remember. */
    val isEditable: Boolean get() = !forgot

    /** Tick or untick "don't remember", dropping whatever was typed when it is ticked. */
    fun withForgot(forgot: Boolean): LastServiceState =
        if (forgot) LastServiceState(forgot = true) else copy(forgot = false)
}
