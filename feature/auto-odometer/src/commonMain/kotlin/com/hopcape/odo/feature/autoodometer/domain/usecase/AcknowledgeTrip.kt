package com.hopcape.odo.feature.autoodometer.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/**
 * Advances the last-acknowledged marker (D4) to [tripEndedAt] — the trip-logged screen's
 * "Done". Takes the trip's `endedAt` directly rather than a [com.hopcape.odo.core.domain.trip.model.TripId],
 * since the caller already has the loaded [com.hopcape.odo.core.domain.trip.model.Trip] and
 * this is the only field the marker needs.
 */
internal class AcknowledgeTrip(
    private val settings: AppSettingsRepository,
) {
    suspend operator fun invoke(tripEndedAt: Instant): Either<DomainError, Unit> {
        val current = settings.observe().first()
        return settings.save(current.copy(aoLastAckedTripEndedAt = tripEndedAt)).map { }
    }
}
