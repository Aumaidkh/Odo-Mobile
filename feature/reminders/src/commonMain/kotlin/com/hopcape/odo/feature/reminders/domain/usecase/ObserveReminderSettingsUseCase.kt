package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The notification preferences the reminder settings screen edits.
 *
 * The same [NotificationPreferences] the profile's notifications screen reads — one
 * store, two doors — which is exactly why this is a thin projection of
 * [AppSettingsRepository] rather than a second settings source that could disagree
 * with it.
 */
internal class ObserveReminderSettingsUseCase(
    private val appSettings: AppSettingsRepository,
) {
    operator fun invoke(): Flow<NotificationPreferences> =
        appSettings.observe()
            .map { it.notifications }
            .distinctUntilChanged()
}
