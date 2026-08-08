package com.hopcape.odo.core.data.trip

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Local persistence for trips and the per-car parked location. Hides the SQLDelight
 * database from [TripRepositoryImpl]: this owns how rows are read and written, the
 * repository owns what an operation means (error mapping, telemetry).
 *
 * Write methods throw on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`. The observe flows are raw — a read failure
 * propagates to the caller, and the repository decides how to report it.
 */
interface TripLocalDataSource {

    suspend fun insert(trip: Trip)

    /** Inserts [trip], its optional GAP_INFERRED [gap] twin, and upserts [parked] — one transaction. */
    suspend fun insertWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation)

    /** Answers `false` when no live trip with that id exists. */
    suspend fun updateStatus(id: TripId, status: TripStatus): Boolean

    /** Every live trip for [carId], newest first. */
    fun observeByCar(carId: CarId): Flow<List<Trip>>

    fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>>

    /** Trips [TripStatus.counted] that started after [after], oldest first. */
    suspend fun countedSince(carId: CarId, after: Instant): List<Trip>

    /** Trips [TripStatus.counted] that started within [from]..[to], both bounds inclusive, oldest first. */
    suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip>

    suspend fun parkedLocation(carId: CarId): ParkedLocation?

    /** Hard-deletes every trip, the parked location, and any live session row for [carId] — one transaction. */
    suspend fun deleteAllForCar(carId: CarId)
}
