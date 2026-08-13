package com.hopcape.odo.feature.reminders.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Edits a custom reminder. Editing re-runs [CustomReminder.create] with the same id, so
 * validation lives in one place and an edit is rejected for exactly the same reasons a
 * create would be.
 *
 * The paused flag survives the edit: pausing is its own decision
 * ([SetReminderPausedUseCase]), and correcting a typo in the title must not quietly
 * turn a reminder back on.
 */
internal class UpdateCustomReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: CustomReminderScheduler,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        id: ReminderId,
        command: CustomReminderCommand,
    ): EitherNel<DomainError, CustomReminder> = either {
        val existing = ensureNotNull(reminders.observe(id).first()) {
            nonEmptyListOf(DomainError.ReminderNotFound)
        }

        val edited = CustomReminder.create(
            id = existing.id,
            ownerId = existing.ownerId,
            carId = existing.carId,
            title = command.title,
            cadence = command.cadence,
            startsOn = command.startsOn,
            at = command.at,
            today = clock.now().toLocalDateTime(timeZone).date,
            preset = command.preset,
            anchorKm = command.anchorKm,
        ).bind().withPaused(existing.paused)

        val stored = reminders.update(edited)
            .mapLeft { nonEmptyListOf(it) }
            .bind()

        scheduler.refresh()
        stored
    }
}
