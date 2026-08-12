package com.hopcape.odo.core.triptracker.port

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.TripDistance

internal interface RouteDistanceEstimator {
    suspend fun estimate(from: GeoPoint, to: GeoPoint): TripDistance?
}
