package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for cars. Hides the SQLDelight database from [CarRepositoryImpl]:
 * this owns how rows are read and written, the repository owns what an operation means
 * (error mapping, telemetry, asking for a sync).
 *
 * Write methods throw on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`. The observe flows are raw — a read failure
 * propagates to the collector, and the repository decides how to report it.
 */
interface CarLocalDataSource {

    /**
     * Insert [car] as a `PENDING` row. Demotes any existing primary in the same
     * transaction, so the one-primary-per-owner invariant holds locally.
     */
    suspend fun insert(car: Car)

    /**
     * Write [car] over its stored row and return it to `PENDING`. Answers `false` when no
     * live row with that id exists — checked in the same transaction as the write, so a
     * car deleted in between cannot turn into a silent no-op update.
     */
    suspend fun update(car: Car): Boolean

    /**
     * Tombstone the car together with its service logs, documents, fuel fills and
     * reminders, in one transaction.
     */
    suspend fun softDelete(id: CarId)

    /** The primary car as it changes; `null` while there is none. */
    fun observePrimary(): Flow<Car?>

    /** The car with [id] as it changes; `null` while no live row has that id. */
    fun observeById(id: CarId): Flow<Car?>

    /**
     * The vehicle behind a plate [ownerId] has entered before, or `null` when they have not.
     *
     * Returns the five attributes rather than the [Car] on purpose: this answers the
     * "is this your car?" suggestion, and an odometer, a nickname or a car id has no place
     * in a suggestion the owner has not confirmed yet.
     */
    suspend fun vehicleByRegistration(
        ownerId: OwnerId,
        registrationNumber: RegistrationNumber,
    ): RegisteredVehicle?
}
