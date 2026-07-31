package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.presentation.state.FormField
import com.hopcape.odo.feature.servicelog.presentation.state.Submission
import com.hopcape.odo.feature.servicelog.presentation.state.text
import kotlinx.datetime.LocalDate

/**
 * Which entry the form is writing — a new one, or the one it was opened on.
 *
 * Typed rather than an `isEditing` flag, because "editing" is never true on its own: an
 * edit always has an id, and every place that branches on the mode needs it. The flag
 * version made the id someone else's problem, usually a nullable field two layers away.
 */
internal sealed interface ServiceLogFormMode {
    data object Add : ServiceLogFormMode
    data class Edit(val id: ServiceLogId) : ServiceLogFormMode

    val isEditing: Boolean get() = this is Edit
}

/**
 * Add / edit form render state — one screen for both [mode]s, and shared by both list
 * directions (1a & 1b enter the same form).
 *
 * Text fields keep raw [String] input (parsed at the save boundary by [toAddCommand] /
 * [toUpdateCommand]) so partial input survives recomposition; the date is a typed
 * [LocalDate]. Odometer is mandatory — it is Odo's core number — and its guard (present,
 * non-backwards) surfaces as that field's [FormField.error]. [categories] are the "what was
 * done" chips.
 */
internal data class ServiceLogFormUiState(
    val mode: ServiceLogFormMode = ServiceLogFormMode.Add,
    /** An edit is reading the entry it will overwrite; the fields aren't trustworthy yet. */
    val isPrefilling: Boolean = false,
    val workshop: FormField<String> = FormField(value = ""),
    val date: FormField<LocalDate> = FormField(),
    val odometer: FormField<String> = FormField(value = ""),
    val odometerUnit: OdoDistanceUnit = OdoDistanceUnit.KM,
    val amount: FormField<String> = FormField(value = ""),
    val notes: FormField<String> = FormField(value = ""),
    val categories: Set<ServiceCategory> = emptySet(),
    val submission: Submission = Submission.Idle,
) {
    val isEditing: Boolean get() = mode.isEditing

    /**
     * Save is offered once the mandatory odometer has input and no write is in flight.
     * Everything else the domain validates on submit — the button's job is to stop the two
     * cases where pressing it could not possibly work, not to re-implement the rules.
     */
    val canSave: Boolean get() = !submission.isInFlight && odometer.text.isNotBlank()

    /** Drop every field error and the form-level one — what a fresh save attempt starts from. */
    fun clearErrors(): ServiceLogFormUiState = copy(
        workshop = workshop.clearError(),
        date = date.clearError(),
        odometer = odometer.clearError(),
        amount = amount.clearError(),
        notes = notes.clearError(),
        submission = Submission.Idle,
    )
}
