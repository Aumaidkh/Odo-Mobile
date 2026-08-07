package com.hopcape.odo.core.data.servicelog

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for service log entries. Hides the SQLDelight database from
 * [ServiceLogRepositoryImpl]: this owns how rows (and their categories) are read and
 * written, the repository owns what an operation means (error mapping, telemetry,
 * asking for a sync).
 *
 * Write methods throw on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`. The observe flows and [odometerReadings] are raw —
 * a read failure propagates to the caller, and the repository decides how to report it.
 */
internal interface ServiceLogLocalDataSource {

    /** Insert [entry] as a `PENDING` row, together with its categories. */
    suspend fun insert(entry: ServiceLogEntry)

    /**
     * Write [entry] over its stored row and return it to `PENDING`, rewriting its
     * categories wholesale. Answers `false` when no live row with that id exists —
     * checked in the same transaction as the write, so an entry deleted in between
     * cannot turn into a silent no-op update.
     */
    suspend fun update(entry: ServiceLogEntry): Boolean

    /** Tombstone the entry. Its categories stay, since a restored row would need them back. */
    suspend fun softDelete(id: ServiceLogId)

    /** Every live entry for [carId], as it changes. */
    fun observeByCar(carId: CarId): Flow<List<ServiceLogEntry>>

    /** The entry with [id], as it changes; `null` while no live row has that id. */
    fun observeById(id: ServiceLogId): Flow<ServiceLogEntry?>

    /**
     * The car's odometer timeline: its onboarding baseline plus every logged reading,
     * dated. Empty when the car itself does not exist for this owner.
     */
    suspend fun odometerReadings(carId: CarId): List<OdometerReading>

    /** [odometerReadings], as it changes — the query spans `cars` and `service_logs`. */
    fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>>
}
