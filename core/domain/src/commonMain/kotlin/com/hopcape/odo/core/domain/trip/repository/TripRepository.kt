package com.hopcape.odo.core.domain.trip.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface TripRepository {
    suspend fun add(trip: Trip): Either<DomainError, Trip>

    /**
     * Inserts [trip], its paired [gap] twin when the car moved unobserved, and the new
     * [parked] location in one transaction — a trip is never saved without its parked
     * location moving to match.
     */
    suspend fun addWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation): Either<DomainError, Trip>

    fun observe(carId: CarId): Flow<List<Trip>>

    fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>>

    suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit>

    /** Trips the derived odometer counts ([TripStatus.counted]) that started after [after]. */
    suspend fun countedSince(carId: CarId, after: Instant): List<Trip>

    suspend fun parkedLocation(carId: CarId): ParkedLocation?
}
