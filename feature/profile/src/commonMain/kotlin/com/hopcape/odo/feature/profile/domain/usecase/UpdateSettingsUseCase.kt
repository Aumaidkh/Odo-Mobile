package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.NotificationSchedule
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.CustomReminderScheduler
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * Change one part of the device's settings without disturbing the rest.
 *
 * Three sheets each own a slice of one stored row, so each method reads what is stored and
 * saves a copy with its own fields replaced. Sending the whole [AppSettings] from a sheet
 * instead would let the appearance sheet quietly write back stale notification flags.
 *
 * One class rather than three use-case files: the three differ only in which fields they
 * touch, and splitting them would repeat the read-copy-save three times.
 *
 * The notification methods also ask the schedulers to rebuild. A preference that only
 * reached the database would leave the phone delivering on the old one until the next time
 * a document happened to be written.
 */
internal class UpdateSettingsUseCase(
    private val settings: AppSettingsRepository,
    private val documentReminders: DocumentReminderScheduler,
    private val customReminders: CustomReminderScheduler,
) {

    /** Theme and text size, from the appearance sheet. */
    suspend fun appearance(
        theme: ThemePreference,
        largerText: Boolean,
    ): Either<DomainError, AppSettings> =
        update { it.copy(theme = theme, largerText = largerText) }

    /** Distance and fuel-efficiency units, from the units sheet. */
    suspend fun units(
        distanceUnit: DistanceUnit,
        fuelEfficiencyUnit: FuelEfficiencyUnit,
    ): Either<DomainError, AppSettings> =
        update { it.copy(distanceUnit = distanceUnit, fuelEfficiencyUnit = fuelEfficiencyUnit) }

    /** What the owner agreed to be told about, from the notifications screen. */
    suspend fun notifications(
        preferences: NotificationPreferences,
    ): Either<DomainError, AppSettings> =
        update { it.copy(notifications = preferences) }.onRight { rescheduleReminders() }

    /** When those notifications arrive — lead days and the hour, same screen. */
    suspend fun notificationSchedule(
        schedule: NotificationSchedule,
    ): Either<DomainError, AppSettings> =
        update { it.copy(notificationSchedule = schedule) }.onRight { rescheduleReminders() }

    /** Back to the defaults — what "delete my data" leaves behind. */
    suspend fun reset(): Either<DomainError, AppSettings> = settings.save(AppSettings.Default)

    private suspend fun update(change: (AppSettings) -> AppSettings): Either<DomainError, AppSettings> =
        settings.save(change(settings.observe().first()))

    /**
     * Rebuild both schedules from what was just stored.
     *
     * Without this a lead time the owner moved would only reach the OS the next time they
     * touched a document — so the screen would say 60 days while the phone still had 30
     * queued. Both schedulers read the settings themselves, so there is nothing to pass.
     */
    private suspend fun rescheduleReminders() {
        documentReminders.refresh()
        customReminders.refresh()
    }
}
