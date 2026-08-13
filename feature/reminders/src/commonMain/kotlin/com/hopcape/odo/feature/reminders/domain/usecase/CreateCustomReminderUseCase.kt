package com.hopcape.odo.feature.reminders.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Creates a custom reminder: validates the form's input, writes the row, and puts the
 * first occurrence on the notification schedule.
 *
 * Scheduling happens after the write and cannot fail it — the scheduler is best-effort
 * by contract, and a reminder that saved but could not be scheduled still shows in the
 * feed, which is better than losing what the owner typed.
 *
 * Field failures come back together via [EitherNel], so the form can show them all at
 * once.
 */
internal class CreateCustomReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: CustomReminderScheduler,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: CustomReminderCommand,
        carId: CarId,
        ownerId: OwnerId,
    ): EitherNel<DomainError, CustomReminder> = either {
        val reminder = CustomReminder.create(
            id = ReminderId.new(idGenerator),
            ownerId = ownerId,
            carId = carId,
            title = command.title,
            cadence = command.cadence,
            startsOn = command.startsOn,
            at = command.at,
            today = clock.now().toLocalDateTime(timeZone).date,
            preset = command.preset,
            anchorKm = command.anchorKm,
        ).bind()

        val stored = reminders.add(reminder)
            .mapLeft { nonEmptyListOf(it) }
            .bind()

        scheduler.refresh()
        stored
    }
}
