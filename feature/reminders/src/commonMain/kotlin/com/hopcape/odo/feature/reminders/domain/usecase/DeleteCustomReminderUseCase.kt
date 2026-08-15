package com.hopcape.odo.feature.reminders.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler

/**
 * Soft-deletes a custom reminder and takes its occurrences off the notification
 * schedule. Cancellation follows the delete and cannot fail it.
 */
internal class DeleteCustomReminderUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: CustomReminderScheduler,
) {
    suspend operator fun invoke(id: ReminderId): Either<DomainError, Unit> =
        reminders.softDelete(id).onRight { scheduler.refresh() }
}
