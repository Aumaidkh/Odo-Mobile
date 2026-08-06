package com.hopcape.odo.core.domain.reminder.model

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A reminder the owner set up themselves — "check air pressure every 15 days" — as
 * opposed to the derived ones Odo works out from documents and service history.
 *
 * Stored in the `reminders` table with `reminder_type = 'custom'` (an enum value the
 * data slice owes DB_SCHEMA.md). The derived kinds deliberately have no aggregate: they
 * are recomputed from their sources on every read, so only the user-authored ones need
 * an identity and a row.
 *
 * Construct only via [create], which enforces every field invariant and accumulates
 * *all* failures, so the create screen can show them at once. The private constructor
 * guarantees an invalid reminder can never exist.
 *
 * [anchorKm] exists only for a distance cadence: it is the odometer reading the
 * kilometre count starts from. For every other cadence it is `null`, whatever was
 * passed in, so no row carries a number that means nothing.
 */
class CustomReminder private constructor(
    val id: ReminderId,
    val ownerId: OwnerId,
    val carId: CarId,
    /** The ready-made topic this started from; `null` when the owner typed their own. */
    val preset: ReminderPreset?,
    val title: ReminderTitle,
    val cadence: ReminderCadence,
    /** The first day a nudge may fire. For a distance cadence, only a lower bound. */
    val startsOn: LocalDate,
    /** The time of day the nudge fires. */
    val at: LocalTime,
    /** The odometer reading a distance cadence counts from; `null` otherwise. */
    val anchorKm: Int?,
    /** Paused reminders keep their row and their settings but never fire. */
    val paused: Boolean,
    /**
     * The day this reminder was filed. Null until stored: the date comes from the row,
     * so one built by [create] and not yet inserted has no answer.
     */
    val addedOn: LocalDate?,
) {
    /**
     * Pause or resume. The only field that changes through a transition rather than an
     * edit: everything else is re-validated by running [create] again with the same id.
     */
    fun withPaused(paused: Boolean): CustomReminder =
        if (paused == this.paused) {
            this
        } else {
            CustomReminder(
                id = id,
                ownerId = ownerId,
                carId = carId,
                preset = preset,
                title = title,
                cadence = cadence,
                startsOn = startsOn,
                at = at,
                anchorKm = anchorKm,
                paused = paused,
                addedOn = addedOn,
            )
        }

    companion object {
        /**
         * Validating factory — the single entry point for building a reminder. [today]
         * is injected (the domain owns no clock) so the start-date guard is
         * deterministic and testable.
         *
         * A start before [today] is rejected: the nudge it names has already not fired,
         * so storing it would create a reminder that is late on arrival. Editing an old
         * reminder re-runs this check, which is right — a saved edit restarts the
         * schedule from a day the owner can still act on.
         */
        fun create(
            id: ReminderId,
            ownerId: OwnerId,
            carId: CarId,
            title: String?,
            cadence: ReminderCadence,
            startsOn: LocalDate,
            at: LocalTime,
            today: LocalDate,
            preset: ReminderPreset? = null,
            anchorKm: Int? = null,
        ): EitherNel<DomainError, CustomReminder> = either {
            zipOrAccumulate(
                { ReminderTitle.of(title).bind() },
                { validateCadence(cadence).bind() },
                { validateStartsOn(startsOn, today).bind() },
                { validateAnchor(cadence, anchorKm).bind() },
            ) { validTitle, validCadence, validStartsOn, validAnchor ->
                CustomReminder(
                    id = id,
                    ownerId = ownerId,
                    carId = carId,
                    preset = preset,
                    title = validTitle,
                    cadence = validCadence,
                    startsOn = validStartsOn,
                    at = at,
                    anchorKm = validAnchor,
                    paused = false,
                    addedOn = null,
                )
            }
        }

        /**
         * Rehydrate a reminder from already-persisted, trusted data. Unlike [create],
         * this does not accumulate errors: a value that fails to reconstruct signals
         * local data corruption and fails fast. The start-in-past check is deliberately
         * absent — every stored reminder's start date ends up in the past eventually,
         * and that is history, not corruption.
         */
        fun reconstitute(
            id: ReminderId,
            ownerId: OwnerId,
            carId: CarId,
            title: String,
            cadence: ReminderCadence,
            startsOn: LocalDate,
            at: LocalTime,
            paused: Boolean,
            addedOn: LocalDate?,
            preset: ReminderPreset? = null,
            anchorKm: Int? = null,
        ): CustomReminder = CustomReminder(
            id = id,
            ownerId = ownerId,
            carId = carId,
            preset = preset,
            title = ReminderTitle.of(title)
                .getOrElse { error("corrupt reminders.title for ${id.value}") },
            cadence = validateCadence(cadence)
                .getOrElse { error("corrupt reminders cadence for ${id.value}") },
            startsOn = startsOn,
            at = at,
            anchorKm = validateAnchor(cadence, anchorKm)
                .getOrElse { error("corrupt reminders anchor for ${id.value}") },
            paused = paused,
            addedOn = addedOn,
        )

        private fun validateCadence(cadence: ReminderCadence): Either<DomainError, ReminderCadence> =
            when (cadence) {
                is ReminderCadence.EveryDays ->
                    if (cadence.days <= 0) DomainError.ReminderIntervalNotPositive.left() else cadence.right()
                is ReminderCadence.EveryDistance ->
                    if (cadence.km <= 0) DomainError.ReminderIntervalNotPositive.left() else cadence.right()
                ReminderCadence.Once, ReminderCadence.Monthly -> cadence.right()
            }

        private fun validateStartsOn(
            startsOn: LocalDate,
            today: LocalDate,
        ): Either<DomainError, LocalDate> =
            if (startsOn < today) DomainError.ReminderStartInPast.left() else startsOn.right()

        private fun validateAnchor(
            cadence: ReminderCadence,
            anchorKm: Int?,
        ): Either<DomainError, Int?> = when {
            cadence !is ReminderCadence.EveryDistance -> null.right()
            anchorKm == null -> DomainError.MissingReminderAnchorOdometer.left()
            anchorKm < 0 -> DomainError.NegativeOdometer.left()
            else -> anchorKm.right()
        }
    }
}
