package com.hopcape.odo.core.domain.trip.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * A device-local latitude/longitude pair.
 *
 * Trip start/end coordinates and the parked location never leave the device — they
 * never sync, never appear in logs, analytics, or crash reports (owner decision
 * 2026-08-07). Every point on this device passed through [of], so no coordinate a
 * caller holds can be out of range.
 *
 * A plain `class`, not a `data class`: a generated `copy()` would let a caller build
 * an out-of-range point straight from a valid one, bypassing [of] entirely.
 */
class GeoPoint private constructor(val lat: Double, val lon: Double) {

    override fun equals(other: Any?): Boolean =
        other is GeoPoint && lat == other.lat && lon == other.lon

    override fun hashCode(): Int = 31 * lat.hashCode() + lon.hashCode()

    override fun toString(): String = "GeoPoint(lat=$lat, lon=$lon)"

    companion object {
        private val LAT_RANGE = -90.0..90.0
        private val LON_RANGE = -180.0..180.0

        fun of(lat: Double, lon: Double): Either<DomainError, GeoPoint> = when {
            lat !in LAT_RANGE -> DomainError.InvalidCoordinate("lat", lat).left()
            lon !in LON_RANGE -> DomainError.InvalidCoordinate("lon", lon).left()
            else -> GeoPoint(lat, lon).right()
        }
    }
}
