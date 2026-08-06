package com.hopcape.odo.feature.reminders.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Records "not this one" for a single occurrence — the actions sheet's dismiss/snooze.
 *
 * The feed looks past a dismissed occurrence to the next one, so this never turns a
 * reminder off: dismissing the 30-day insurance nudge leaves the 7-day one coming, and
 * dismissing this fortnight's check leaves next fortnight's. The row carries which
 * occurrence via [UpcomingReminder.dismissalKey][com.hopcape.odo.core.domain.reminder.model.UpcomingReminder.dismissalKey].
 */
internal class DismissReminderUseCase(
    private val reminders: ReminderRepository,
) {
    suspend operator fun invoke(
        carId: CarId,
        dismissal: ReminderDismissal,
    ): Either<DomainError, Unit> = reminders.dismiss(carId, dismissal)
}
