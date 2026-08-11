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

    /**
     * Trips the derived odometer counts ([TripStatus.counted]) that started within
     * [from]..[to], both bounds inclusive — the monthly-stats window (auto-odometer plan
     * §4.1's `ObserveMonthlySummary`), which [countedSince] can't serve on its own.
     */
    suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip>

    suspend fun parkedLocation(carId: CarId): ParkedLocation?

    /**
     * Hard-deletes every trip and the parked location for [carId] (M7's "Delete all trip
     * data") — local-only today. Once trip sync lands this must become tombstoning instead
     * of a real delete, so a push doesn't resurrect the rows on another device.
     */
    suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit>

    /**
     * Drop every stored coordinate, keeping the trips themselves.
     *
     * What "Keep trip routes" turning off has to do to the past, not only the future. The
     * switch's line reads "only distance is stored", and that has to be true of trips
     * already recorded or it is not a privacy control — it is a promise about tomorrow.
     *
     * Distances survive untouched: they were integrated during the trip, not recomputed
     * from the points being erased, so nothing the owner sees changes.
     *
     * Parked locations go too. They are a coordinate the app kept, which is what the switch
     * is about. The cost is that GPS-only trips lose their attribution anchor and land as
     * `NEEDS_CONFIRMATION` until the owner leaves the car somewhere with the switch back on
     * — the honest trade for asking the app to forget where the car is.
     */
    suspend fun forgetRoutes(): Either<DomainError, Unit>
}
