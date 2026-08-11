package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first

/**
 * Move one of the three privacy switches.
 *
 * The three look alike on screen and are stored in two different places, which is the whole
 * reason this exists: two describe the device and live in [AppSettings], while price sharing
 * describes the account and lives on the profile so it reaches the server. A screen should
 * not have to know which is which.
 *
 * Each method reads what is stored and writes a copy with its own field replaced, the same
 * read-copy-save shape as [UpdateSettingsUseCase] — sending a whole object down from the
 * screen would let one switch write back another's stale value.
 */
internal class UpdatePrivacyUseCase(
    private val settings: AppSettingsRepository,
    private val profiles: OwnerProfileRepository,
    private val trips: TripRepository,
) {

    /**
     * Keep the coordinates of automatically-detected trips, or only their distance.
     *
     * Turning it off erases what is already stored, not just what happens next. "Only
     * distance is stored" has to be true of the trips on the phone right now, or the switch
     * is a promise about the future rather than a privacy control.
     *
     * The purge runs *after* the setting is stored, and only if that succeeded. The other
     * order would erase an owner's routes and then leave the switch showing on, which is
     * the one combination that cannot be undone or explained.
     */
    suspend fun keepTripRoutes(enabled: Boolean): Either<DomainError, AppSettings> =
        updateSettings { it.copy(privacy = it.privacy.copy(keepTripRoutes = enabled)) }
            .flatMap { saved ->
                if (enabled) saved.right() else trips.forgetRoutes().map { saved }
            }

    /** Count which screens and features get used. */
    suspend fun usageAnalytics(enabled: Boolean): Either<DomainError, AppSettings> =
        updateSettings { it.copy(privacy = it.privacy.copy(usageAnalytics = enabled)) }

    /**
     * Let this owner's prices feed the city benchmark.
     *
     * Fails with [DomainError.ProfileNotFound] when there is no profile row at all. That is
     * not reachable from the privacy screen — the app writes a profile during onboarding and
     * this screen sits behind it — but the alternative would be to create one here, and a
     * settings write is the wrong place to invent an owner.
     */
    suspend fun sharePrices(enabled: Boolean): Either<DomainError, Unit> {
        val profile = profiles.observe().first() ?: return DomainError.ProfileNotFound.left()
        return profiles.save(profile.withPriceSharing(enabled)).map { }
    }

    private suspend fun updateSettings(
        change: (AppSettings) -> AppSettings,
    ): Either<DomainError, AppSettings> = settings.save(change(settings.observe().first()))
}
