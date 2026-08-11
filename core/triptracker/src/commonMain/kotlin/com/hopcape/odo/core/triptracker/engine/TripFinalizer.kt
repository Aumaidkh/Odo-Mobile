package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripDistance
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.triptracker.algorithm.TripValidator
import com.hopcape.odo.core.triptracker.algorithm.haversineMeters
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.observability.TripTrackerTelemetry
import com.hopcape.odo.core.triptracker.port.RouteDistanceEstimator
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/**
 * Turns a finished [TripSession] into a saved [Trip] (§4.4). For a BT-verified trip that
 * started far from the stored parked location, also inserts the paired GAP_INFERRED twin
 * for the segment the car covered while unwatched (§4.5) — a GPS-only trip never needs
 * one, since the parked-location check already refused to start it in that case.
 */
internal class TripFinalizer(
    private val ids: IdGenerator,
    private val tripRepository: TripRepository,
    private val routeEstimator: RouteDistanceEstimator,
    private val config: TripTrackerConfig,
    private val telemetry: TripTrackerTelemetry,
    private val settings: AppSettingsRepository,
) {
    private val validator = TripValidator(config)

    suspend fun finalize(session: TripSession, endedAt: Instant, carId: CarId, ownerId: OwnerId, parked: ParkedLocation?) {
        val verdict = validator.validate(
            distanceMeters = session.distanceMeters,
            estimatedMeters = session.estimatedMeters,
            duration = endedAt - session.startedAt,
            attributionMarginal = !session.attributionConfident,
        )
        val status = verdict.status
        if (!verdict.save || status == null) {
            telemetry.discarded(session.mode, session.distanceMeters, reason = TripTrackerTelemetry.REASON_BELOW_FLOOR)
            return
        }

        // The privacy switch, read at the moment of writing rather than when the trip
        // started: a drive that began before the owner turned routes off must not leave
        // coordinates behind because it was already in flight.
        val keepRoutes = settings.observe().first().privacy.keepTripRoutes

        val startPoint = session.firstFix
            ?.takeIf { keepRoutes }
            ?.let { GeoPoint.of(it.lat, it.lon).getOrNull() }
        val endPoint = session.lastGoodFix
            ?.takeIf { keepRoutes }
            ?.let { GeoPoint.of(it.lat, it.lon).getOrNull() }

        val trip = Trip.create(
            id = TripId.new(ids),
            carId = carId,
            ownerId = ownerId,
            startedAt = session.startedAt,
            endedAt = endedAt,
            distance = TripDistance.of(session.distanceMeters),
            estimatedDistance = TripDistance.of(session.estimatedMeters),
            mode = session.mode,
            status = status,
            startLat = startPoint?.lat,
            startLon = startPoint?.lon,
            endLat = endPoint?.lat,
            endLon = endPoint?.lon,
        ).getOrNull() ?: run {
            telemetry.discarded(session.mode, session.distanceMeters, reason = TripTrackerTelemetry.REASON_INVALID)
            return
        }

        val gap = gapInferredTwin(session, carId, ownerId, startPoint, parked)
        val newParked = endPoint?.let { ParkedLocation(carId, it, endedAt) }

        val result = if (newParked != null) {
            tripRepository.addWithParked(trip, gap, newParked)
        } else {
            tripRepository.add(trip)
        }

        result.fold(
            { error ->
                // The trip the owner just drove never made it to storage — distinct from a
                // validation-ladder discard, so it gets its own signal rather than none.
                telemetry.nonFatal(IllegalStateException("trip repository write failed: $error"), stage = STAGE_WRITE)
            },
            {
                telemetry.saved(trip.mode, trip.status == TripStatus.NEEDS_CONFIRMATION, trip.distance.meters, trip.estimatedDistance.meters)
                if (gap != null) telemetry.gapInferred()
            },
        )
    }

    private suspend fun gapInferredTwin(
        session: TripSession,
        carId: CarId,
        ownerId: OwnerId,
        startPoint: GeoPoint?,
        parked: ParkedLocation?,
    ): Trip? {
        if (session.mode != TripMode.BT_VERIFIED || startPoint == null || parked == null) return null
        if (haversineMeters(startPoint, parked.point) <= config.attributionRadiusM) return null

        val estimate = routeEstimator.estimate(parked.point, startPoint) ?: return null
        return Trip.create(
            id = TripId.new(ids),
            carId = carId,
            ownerId = ownerId,
            startedAt = parked.recordedAt,
            endedAt = session.startedAt,
            distance = estimate,
            estimatedDistance = estimate,
            mode = TripMode.GAP_INFERRED,
            status = TripStatus.NEEDS_CONFIRMATION,
            startLat = parked.point.lat,
            startLon = parked.point.lon,
            endLat = startPoint.lat,
            endLon = startPoint.lon,
        ).getOrNull()
    }

    private companion object {
        const val STAGE_WRITE = "finalize_write"
    }
}
