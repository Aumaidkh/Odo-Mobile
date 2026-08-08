package com.hopcape.odo.core.domain.trip.model

import com.hopcape.odo.core.domain.car.model.CarId
import kotlin.time.Instant

/**
 * Where a car was last left — the anchor a new GPS-only trip's start is checked
 * against before the engine will attribute it (§4.5 of the trip tracker plan). One
 * row per car; a trip is never saved without this moving to match, or the next
 * trip's attribution check would compare against a stale spot.
 */
data class ParkedLocation(
    val carId: CarId,
    val point: GeoPoint,
    val recordedAt: Instant,
)
