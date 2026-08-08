package com.hopcape.odo.infrastructure.database.trip

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import kotlin.time.Instant

/** Row → domain. Sync columns are accepted and deliberately ignored — the domain never sees them. */
@Suppress("LongParameterList", "UNUSED_PARAMETER")
internal fun tripFromRow(
    id: String,
    carId: String,
    ownerId: String,
    startedAt: String,
    endedAt: String,
    distanceM: Long,
    estimatedM: Long,
    mode: String,
    status: String,
    startLat: Double?,
    startLon: Double?,
    endLat: Double?,
    endLon: Double?,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    remoteVersion: String?,
    syncStatus: String,
): Trip = Trip.reconstitute(
    id = TripId(id),
    carId = CarId(carId),
    ownerId = OwnerId(ownerId),
    startedAt = Instant.parse(startedAt),
    endedAt = Instant.parse(endedAt),
    distanceMeters = distanceM,
    estimatedMeters = estimatedM,
    mode = TripMode.valueOf(mode),
    status = TripStatus.valueOf(status),
    startLat = startLat,
    startLon = startLon,
    endLat = endLat,
    endLon = endLon,
)

internal fun parkedLocationFromRow(carId: String, lat: Double, lon: Double, recordedAt: String): ParkedLocation {
    val point = GeoPoint.of(lat, lon).getOrElse { error("corrupt parked_locations row for car $carId: lat=$lat lon=$lon") }
    return ParkedLocation(CarId(carId), point, Instant.parse(recordedAt))
}
