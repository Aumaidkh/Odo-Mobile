package com.hopcape.odo.core.data.health

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Local persistence for health score snapshots. Hides the SQLDelight database from
 * [HealthScoreRepositoryImpl]: this owns how rows are read and written, the repository
 * owns what an operation means (error mapping, telemetry, asking for a sync).
 *
 * Append-only: a snapshot records what was true at a moment, so there is no update or
 * delete here — only insert and read.
 *
 * [insert] throws on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`. The reads and [observeHistory] are raw — a failure
 * propagates to the caller, and the repository decides how to report it.
 */
internal interface HealthScoreLocalDataSource {

    /** Insert [snapshot] as a `PENDING` row. */
    suspend fun insert(snapshot: HealthSnapshot)

    /** The car's most recent snapshot, or `null` with no history yet. */
    suspend fun latest(carId: CarId): HealthSnapshot?

    /** The car's most recent snapshot computed at or before [instant], or `null`. */
    suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot?

    /** Every snapshot for [carId], as it changes. */
    fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>>
}
