package com.hopcape.odo.feature.servicelog.presentation.form

import com.hopcape.odo.core.designsystem.text.DistanceArg
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.displayValue
import com.hopcape.odo.core.domain.shared.of
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogCommand
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogCommand
import com.hopcape.odo.feature.servicelog.presentation.state.text
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_amount_negative
import com.hopcape.odo.feature.servicelog.resources.sl_error_date_future
import com.hopcape.odo.feature.servicelog.resources.sl_error_date_required
import com.hopcape.odo.feature.servicelog.resources.sl_error_notes_too_long
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_ahead
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_negative
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_regression
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_required
import com.hopcape.odo.feature.servicelog.resources.sl_error_workshop_too_long

/**
 * The seam between what the owner typed and what the domain accepts: raw text in, commands
 * and field errors out.
 *
 * Pure functions, deliberately outside the ViewModel. Everything here is a *translation* —
 * "54,000" is 54000 km, `NegativeAmount` belongs on the amount field — and none of it is a
 * rule: the rules stay in `ServiceLogEntry.create` and the odometer timeline, which is why
 * this file never decides whether a value is acceptable, only what to call it.
 */

/** The form as a new-entry command. Blank optional fields travel as `null`, not "". */
internal fun ServiceLogFormUiState.toAddCommand(): AddServiceLogCommand = AddServiceLogCommand(
    serviceDate = date.value,
    odometerKm = odometerKm(),
    totalAmountPaise = amountPaise(),
    workshopName = workshop.text.trimToNull(),
    notes = notes.text.trimToNull(),
    categories = categories,
)

/**
 * The form as an edit of [original].
 *
 * The entry's [ServiceLogEntry.lineItems] and bill photo are carried over untouched: the
 * form doesn't show either, and a command that omitted them would quietly strip the priced
 * breakdown — and, with the photo, the entry's Verified badge — from an owner who only
 * meant to fix a date.
 */
internal fun ServiceLogFormUiState.toUpdateCommand(
    id: ServiceLogId,
    carId: CarId,
    ownerId: OwnerId,
    original: ServiceLogEntry,
): UpdateServiceLogCommand = UpdateServiceLogCommand(
    id = id,
    carId = carId,
    ownerId = ownerId,
    serviceDate = date.value,
    odometerKm = odometerKm(),
    totalAmountPaise = amountPaise(),
    workshopName = workshop.text.trimToNull(),
    notes = notes.text.trimToNull(),
    categories = categories,
    lineItems = original.lineItems.map(ServiceLogLineItem::toDraft),
    billPhotoRef = original.billPhotoRef,
)

/** An existing entry back into the form it will be edited in. */
internal fun ServiceLogFormUiState.prefilledFrom(entry: ServiceLogEntry): ServiceLogFormUiState = copy(
    workshop = workshop.update(entry.workshopName?.value.orEmpty()),
    date = date.update(entry.serviceDate),
    // Shown in the owner's unit, which is the one the field is labelled with. The stored
    // reading is always kilometres; nothing about the entry changes by looking at it.
    odometer = odometer.update(entry.odometer.displayValue(distanceUnit).toString()),
    amount = amount.update(entry.totalAmount.paise.toRupeeInput()),
    notes = notes.update(entry.notes?.value.orEmpty()),
    categories = entry.categories,
    isPrefilling = false,
)

/**
 * The odometer as whole kilometres, or `null` when nothing usable was typed — which the
 * domain answers with [DomainError.MissingOdometer], the same as an empty field.
 */
private fun ServiceLogFormUiState.odometerKm(): Int? {
    val value = odometer.text.digitsOrNull()?.toIntOrNull() ?: return null
    // The domain owns the conversion, so a reading typed in miles round-trips the same way
    // it does everywhere else in the app.
    return Distance.of(value, distanceUnit).getOrNull()?.km
}

/**
 * The amount as integer paise. Money is paise everywhere below the UI (CLAUDE.md), so the
 * rupees the owner typed are converted exactly once — here — and never travel as a Double.
 */
private fun ServiceLogFormUiState.amountPaise(): Long? {
    val cleaned = amount.text.filter { it.isDigit() || it == '.' }
    if (cleaned.isEmpty()) return null
    val rupees = cleaned.substringBefore('.').toLongOrNull() ?: return null
    val paise = cleaned.substringAfter('.', "").take(2).padEnd(2, '0').toLongOrNull() ?: 0L
    return rupees * PAISE_PER_RUPEE + paise
}

/**
 * The field a validation failure belongs to, so the message lands where the owner can act
 * on it. `null` means no field owns it — a storage failure isn't any one answer's fault,
 * and the form reports it at form level instead.
 */
internal fun DomainError.toFieldError(): ServiceLogFormFieldError? = when (this) {
    DomainError.MissingOdometer ->
        ServiceLogFormFieldError(ServiceLogFormField.ODOMETER, UiText(Res.string.sl_error_odometer_required))

    DomainError.NegativeOdometer ->
        ServiceLogFormFieldError(ServiceLogFormField.ODOMETER, UiText(Res.string.sl_error_odometer_negative))

    is DomainError.OdometerRegression ->
        ServiceLogFormFieldError(
            ServiceLogFormField.ODOMETER,
            UiText(Res.string.sl_error_odometer_regression, listOf(DistanceArg(previousKm))),
        )

    is DomainError.OdometerAheadOfLaterEntry ->
        ServiceLogFormFieldError(
            ServiceLogFormField.ODOMETER,
            UiText(Res.string.sl_error_odometer_ahead, listOf(DistanceArg(nextKm))),
        )

    DomainError.MissingServiceDate ->
        ServiceLogFormFieldError(ServiceLogFormField.DATE, UiText(Res.string.sl_error_date_required))

    DomainError.ServiceDateInFuture ->
        ServiceLogFormFieldError(ServiceLogFormField.DATE, UiText(Res.string.sl_error_date_future))

    DomainError.NegativeAmount ->
        ServiceLogFormFieldError(ServiceLogFormField.AMOUNT, UiText(Res.string.sl_error_amount_negative))

    is DomainError.WorkshopNameTooLong ->
        ServiceLogFormFieldError(ServiceLogFormField.WORKSHOP, UiText(Res.string.sl_error_workshop_too_long))

    is DomainError.NotesTooLong ->
        ServiceLogFormFieldError(ServiceLogFormField.NOTES, UiText(Res.string.sl_error_notes_too_long))

    else -> null
}

/** The form's error-bearing fields. */
internal enum class ServiceLogFormField { WORKSHOP, DATE, ODOMETER, AMOUNT, NOTES }

internal data class ServiceLogFormFieldError(
    val field: ServiceLogFormField,
    val message: UiText,
)

private fun ServiceLogLineItem.toDraft() = ServiceLogLineItemDraft(
    label = label,
    category = category,
    amountPaise = amount.paise,
)

/** Digits only — the grouping separators the input component adds are not input. */
private fun String.digitsOrNull(): String? = filter { it.isDigit() }.ifEmpty { null }

private fun String.trimToNull(): String? = trim().ifEmpty { null }

/** Paise back into the rupee text the amount field shows; whole rupees stay whole. */
private fun Long.toRupeeInput(): String {
    val rupees = this / PAISE_PER_RUPEE
    val paise = this % PAISE_PER_RUPEE
    return if (paise == 0L) rupees.toString() else "$rupees.${paise.toString().padStart(2, '0')}"
}

private const val PAISE_PER_RUPEE = 100L

/** Exact by definition (international mile), so a reading converts the same way every time. */
