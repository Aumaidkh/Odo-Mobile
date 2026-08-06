package com.hopcape.odo.feature.reminders.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first

/**
 * Stores an edited set of notification preferences, leaving every other app setting
 * (theme, units, text size) exactly as it was.
 */
internal class UpdateReminderSettingsUseCase(
    private val appSettings: AppSettingsRepository,
) {
    suspend operator fun invoke(
        preferences: NotificationPreferences,
    ): Either<DomainError, NotificationPreferences> {
        val current = appSettings.observe().first()
        return appSettings.save(current.copy(notifications = preferences))
            .map { it.notifications }
    }
}
