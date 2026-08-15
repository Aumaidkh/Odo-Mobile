package com.hopcape.odo.feature.reminders.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * Pauses or resumes a custom reminder — the actions sheet's "turn off" without losing
 * the reminder's settings. A paused reminder keeps its row; only its nudges stop.
 */
internal class SetReminderPausedUseCase(
    private val reminders: ReminderRepository,
    private val scheduler: CustomReminderScheduler,
) {
    suspend operator fun invoke(
        id: ReminderId,
        paused: Boolean,
    ): Either<DomainError, CustomReminder> = either {
        val existing = ensureNotNull(reminders.observe(id).first()) { DomainError.ReminderNotFound }

        val stored = reminders.update(existing.withPaused(paused)).bind()

        scheduler.refresh()
        stored
    }
}
