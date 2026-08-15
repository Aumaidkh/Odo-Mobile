package com.hopcape.odo.core.data.cost

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFill
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for fuel fills. Hides the SQLDelight database from
 * [FuelFillRepositoryImpl]: this owns how the row is written and read, the repository owns
 * what an operation means (error mapping, telemetry, asking for a sync).
 *
 * Throws on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`.
 */
interface FuelFillLocalDataSource {

    /** Insert [fill] as a `PENDING` row. */
    suspend fun insert(fill: FuelFill)

    /** The car's fills, newest first, as a stream. Tombstoned rows are excluded. */
    fun observeByCar(carId: CarId): Flow<List<FuelFill>>

    /** The car's most recent fill, or `null` when it has none. */
    suspend fun latestForCar(carId: CarId): FuelFill?

    /** How many of the car's fills a given channel produced. */
    suspend fun countBySource(carId: CarId, source: FillEntrySource): Int
}
