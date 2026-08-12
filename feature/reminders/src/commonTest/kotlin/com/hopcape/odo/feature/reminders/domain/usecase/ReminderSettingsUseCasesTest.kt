package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.reminders.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSettingsUseCasesTest {

    @Test
    fun observeProjectsTheNotificationHalfOfSettings() = runTest {
        val stored = AppSettings(
            theme = ThemePreference.DARK,
            notifications = NotificationPreferences(whatsapp = true, partnerOffers = true),
        )

        val observed = ObserveReminderSettingsUseCase(FakeAppSettingsRepository(stored))
            .invoke()
            .first()

        assertEquals(stored.notifications, observed)
    }

    @Test
    fun updateTouchesOnlyTheNotificationHalf() = runTest {
        val repository = FakeAppSettingsRepository(AppSettings(theme = ThemePreference.DARK))
        val edited = NotificationPreferences(serviceDue = false, whatsapp = true)

        val result = UpdateReminderSettingsUseCase(repository).invoke(edited)

        assertEquals(edited, result.getOrNull())
        assertEquals(edited, repository.settings.notifications)
        // The theme is not the reminder screen's to change.
        assertEquals(ThemePreference.DARK, repository.settings.theme)
    }

    @Test
    fun aFailedWriteIsReported() = runTest {
        val repository = FakeAppSettingsRepository(
            failWith = DomainError.PersistenceFailure("disk full"),
        )

        val result = UpdateReminderSettingsUseCase(repository).invoke(NotificationPreferences())

        assertTrue(result.isLeft())
    }
}
