package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The newest unresolved trip newer than the last-acknowledged marker (D4) — what the shared
 * app shell redirects to the trip-logged screen (M6). `null` when there is nothing new to
 * show, which includes a car with no trips at all.
 *
 * Candidates are every status except [TripStatus.REJECTED] — a
 * [TripStatus.NEEDS_CONFIRMATION] trip belongs here too, not only an already-
 * [counted][TripStatus.counted] one: this screen's "Done" is exactly what promotes it to
 * [TripStatus.CONFIRMED] (see [com.hopcape.odo.feature.autoodometer.presentation.triplogged.TripLoggedViewModel]),
 * so excluding it here would mean the owner is never shown the one trip that most needs
 * reviewing. The marker only advances on [AcknowledgeTrip], so an ignored screen keeps
 * returning the same trip on the next open.
 */
internal class ObservePendingTripLogged(
    private val trips: TripRepository,
    private val settings: AppSettingsRepository,
) {
    operator fun invoke(carId: CarId): Flow<TripId?> = combine(
        trips.observe(carId),
        settings.observe(),
    ) { allTrips, appSettings ->
        val marker = appSettings.aoLastAckedTripEndedAt
        allTrips
            .filter { it.status != TripStatus.REJECTED }
            .filter { marker == null || it.endedAt > marker }
            .maxByOrNull { it.endedAt }
            ?.id
    }
}
