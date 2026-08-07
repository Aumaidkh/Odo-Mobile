package com.hopcape.odo.core.data.health

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLDelight-backed [HealthScoreLocalDataSource] — fully offline. The local DB is the
 * source of truth; every write stamps `updated_at` and leaves the row
 * `sync_status = PENDING` for the sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 */
internal class SqlDelightHealthScoreLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HealthScoreLocalDataSource {

    private val queries get() = database.healthScoreQueries

    override suspend fun insert(snapshot: HealthSnapshot) {
        val now = clock.now().toString()
        queries.insertSnapshot(
            id = snapshot.id.value,
            carId = snapshot.carId.value,
            ownerId = snapshot.ownerId.value,
            score = snapshot.score.total.toLong(),
            maintenancePts = snapshot.score.pointsFor(HealthFactorKind.MAINTENANCE),
            documentationPts = snapshot.score.pointsFor(HealthFactorKind.DOCUMENTATION),
            costEfficiencyPts = snapshot.score.pointsFor(HealthFactorKind.COST_EFFICIENCY),
            historyPts = snapshot.score.pointsFor(HealthFactorKind.HISTORY),
            // The snapshot's own version, not whatever this build computes today: the
            // caller stamped it when the score was taken, and a row that claims newer
            // rules than the ones that produced it is a false comparison later.
            algoVersion = snapshot.algoVersion,
            computedAt = snapshot.computedAt.toString(),
            now = now,
            syncStatus = SyncStatus.PENDING.name,
        )
    }

    override suspend fun latest(carId: CarId): HealthSnapshot? =
        queries.selectLatest(carId.value).executeAsOneOrNull()?.toDomain()

    override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? =
        queries.selectLatestOnOrBefore(carId.value, instant.toString()).executeAsOneOrNull()?.toDomain()

    override fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>> =
        queries.selectHistory(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }
}
