package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.trip.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle distance between two lat/lon pairs, in metres. */
internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = lat1.toRadians()
    val phi2 = lat2.toRadians()
    val dPhi = (lat2 - lat1).toRadians()
    val dLambda = (lon2 - lon1).toRadians()

    val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
    return EARTH_RADIUS_M * 2 * asin(sqrt(a))
}

internal fun haversineMeters(from: GeoPoint, to: GeoPoint): Double =
    haversineMeters(from.lat, from.lon, to.lat, to.lon)

private fun Double.toRadians(): Double = this * PI / 180.0
