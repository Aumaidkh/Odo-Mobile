package com.hopcape.odo.core.domain.trip.model

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Instant

/**
 * A single automatically-detected drive.
 *
 * Construct only via [create], which enforces every invariant and accumulates *all*
 * validation failures, or via [reconstitute] for a trip already in the database.
 * [withStatus] is the one field that changes after that: everything else about a trip
 * is fixed the moment the engine finalizes it. The private constructor guarantees an
 * invalid [Trip] can never exist.
 *
 * [estimatedDistance] is the portion of [distance] that came from a gap-fill route
 * estimate rather than a live fix — always `<= distance`, enforced as a hard
 * invariant rather than a recoverable error: it is a relationship the finalizer
 * builds by construction, never a value a caller types in.
 */
class Trip private constructor(
    val id: TripId,
    val carId: CarId,
    val ownerId: OwnerId,
    val startedAt: Instant,
    val endedAt: Instant,
    val distance: TripDistance,
    val estimatedDistance: TripDistance,
    val mode: TripMode,
    val status: TripStatus,
    val startPoint: GeoPoint?,
    val endPoint: GeoPoint?,
) {
    init {
        require(estimatedDistance.meters <= distance.meters) {
            "estimated distance ${estimatedDistance.meters}m exceeds total ${distance.meters}m " +
                "for trip ${id.value}"
        }
    }

    /**
     * The same trip after the owner confirms or rejects it. A named method rather than
     * passing every field back through [create], since nothing else about a finalized
     * trip is meant to change.
     */
    fun withStatus(status: TripStatus): Trip = Trip(
        id = id,
        carId = carId,
        ownerId = ownerId,
        startedAt = startedAt,
        endedAt = endedAt,
        distance = distance,
        estimatedDistance = estimatedDistance,
        mode = mode,
        status = status,
        startPoint = startPoint,
        endPoint = endPoint,
    )

    companion object {
        /**
         * Validating factory — the single entry point for building a [Trip]. Takes raw
         * lat/lon pairs rather than [GeoPoint]s so a bad coordinate accumulates alongside
         * a bad time window instead of failing the whole finalize before either is seen.
         * A pair with only one of lat/lon present is treated as absent, not invalid — a
         * point only means something as a pair.
         */
        fun create(
            id: TripId,
            carId: CarId,
            ownerId: OwnerId,
            startedAt: Instant,
            endedAt: Instant,
            distance: TripDistance,
            estimatedDistance: TripDistance,
            mode: TripMode,
            status: TripStatus,
            startLat: Double? = null,
            startLon: Double? = null,
            endLat: Double? = null,
            endLon: Double? = null,
        ): EitherNel<DomainError, Trip> = either {
            zipOrAccumulate(
                { validateWindow(startedAt, endedAt).bind() },
                { validatePoint(startLat, startLon).bind() },
                { validatePoint(endLat, endLon).bind() },
            ) { _, validStart, validEnd ->
                Trip(
                    id = id,
                    carId = carId,
                    ownerId = ownerId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    distance = distance,
                    estimatedDistance = estimatedDistance,
                    mode = mode,
                    status = status,
                    startPoint = validStart,
                    endPoint = validEnd,
                )
            }
        }

        /**
         * Rehydrate a [Trip] from already-persisted, trusted data. Unlike [create], this
         * does not accumulate validation errors: the row was written by a previously-valid
         * [Trip], so a value that fails to reconstruct signals local data corruption, and
         * fails fast.
         */
        fun reconstitute(
            id: TripId,
            carId: CarId,
            ownerId: OwnerId,
            startedAt: Instant,
            endedAt: Instant,
            distanceMeters: Long,
            estimatedMeters: Long,
            mode: TripMode,
            status: TripStatus,
            startLat: Double?,
            startLon: Double?,
            endLat: Double?,
            endLon: Double?,
        ): Trip = Trip(
            id = id,
            carId = carId,
            ownerId = ownerId,
            startedAt = startedAt,
            endedAt = endedAt,
            distance = TripDistance.of(distanceMeters),
            estimatedDistance = TripDistance.of(estimatedMeters),
            mode = mode,
            status = status,
            startPoint = reconstitutePoint(id, "start", startLat, startLon),
            endPoint = reconstitutePoint(id, "end", endLat, endLon),
        )

        private fun validateWindow(startedAt: Instant, endedAt: Instant): Either<DomainError, Unit> =
            if (endedAt > startedAt) Unit.right() else DomainError.InvalidTripWindow.left()

        private fun validatePoint(lat: Double?, lon: Double?): Either<DomainError, GeoPoint?> =
            if (lat == null || lon == null) {
                Either.Right(null)
            } else {
                GeoPoint.of(lat, lon)
            }

        private fun reconstitutePoint(id: TripId, which: String, lat: Double?, lon: Double?): GeoPoint? =
            if (lat == null || lon == null) {
                null
            } else {
                GeoPoint.of(lat, lon).getOrElse { error("corrupt trip.$which point for ${id.value}") }
            }
    }
}
