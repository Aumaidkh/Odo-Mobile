package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.DomainError
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
 */
internal class UpdateSettingsUseCase(
    private val settings: AppSettingsRepository,
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
        update { it.copy(notifications = preferences) }

    /** Back to the defaults — what "delete my data" leaves behind. */
    suspend fun reset(): Either<DomainError, AppSettings> = settings.save(AppSettings.Default)

    private suspend fun update(change: (AppSettings) -> AppSettings): Either<DomainError, AppSettings> =
        settings.save(change(settings.observe().first()))
}
