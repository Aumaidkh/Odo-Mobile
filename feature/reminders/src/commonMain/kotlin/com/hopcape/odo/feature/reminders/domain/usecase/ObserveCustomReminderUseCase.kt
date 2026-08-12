package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow

/** One custom reminder, for the edit form's prefill; emits `null` if it is gone. */
internal class ObserveCustomReminderUseCase(
    private val reminders: ReminderRepository,
) {
    operator fun invoke(id: ReminderId): Flow<CustomReminder?> = reminders.observe(id)
}
