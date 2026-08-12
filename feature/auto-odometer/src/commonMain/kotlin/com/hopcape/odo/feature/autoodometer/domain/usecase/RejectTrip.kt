package com.hopcape.odo.feature.autoodometer.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository

/**
 * "That wasn't me driving" — rejects a trip so it stops counting toward the derived
 * odometer. [TripStatus.REJECTED] is not [TripStatus.counted], so
 * [ObserveDerivedOdometer] and [ObservePendingTripLogged] both reflow the moment this
 * lands: the drum drops back, and a rejected trip can never surface as pending again.
 */
internal class RejectTrip(
    private val trips: TripRepository,
) {
    suspend operator fun invoke(tripId: TripId): Either<DomainError, Unit> =
        trips.setStatus(tripId, TripStatus.REJECTED)
}
