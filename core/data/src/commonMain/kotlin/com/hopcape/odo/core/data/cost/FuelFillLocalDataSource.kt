package com.hopcape.odo.core.data.cost

import com.hopcape.odo.core.domain.cost.model.FuelFill

/**
 * Local persistence for fuel fills. Hides the SQLDelight database from
 * [FuelFillRepositoryImpl]: this owns how the row is written, the repository owns what an
 * operation means (error mapping, telemetry, asking for a sync).
 *
 * Throws on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`.
 */
internal interface FuelFillLocalDataSource {

    /** Insert [fill] as a `PENDING` row. */
    suspend fun insert(fill: FuelFill)
}
