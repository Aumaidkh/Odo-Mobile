package com.hopcape.odo.core.data.health

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLDelight-backed [HealthScoreRepository] — offline-first. The local DB is the source of
 * truth; every write lands `sync_status = PENDING` for the engine that arrives in M5, and
 * nothing here waits on a network call.
 *
 * There is no remote data source held yet, unlike the service-log and document
 * repositories. Nothing pushes a score today, and the server recomputes its own snapshots
 * from the rows it receives, so the seam is left for the engine to define rather than
 * guessed at now.
 *
 * Timestamps are client-stamped (offline-first; the server reconciles on sync).
 */
internal class HealthScoreRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val clock: Clock = Clock.System,
) : HealthScoreRepository {

    private val queries get() = database.healthScoreQueries

    override suspend fun latest(carId: CarId): HealthSnapshot? =
        telemetry.span(DataTelemetry.HEALTH_SCORE, OP_LATEST, carId.value) {
            try {
                queries.selectLatest(carId.value).executeAsOneOrNull()?.toDomain()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.HEALTH_SCORE, OP_LATEST, e, carId.value)
                // No history reads as no history: the caller writes a fresh snapshot and
                // hides the delta, which is what a car with no past score gets anyway.
                null
            }
        }

    override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? =
        telemetry.span(DataTelemetry.HEALTH_SCORE, OP_LATEST_BEFORE, carId.value) {
            try {
                queries.selectLatestOnOrBefore(carId.value, instant.toString())
                    .executeAsOneOrNull()
                    ?.toDomain()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.HEALTH_SCORE, OP_LATEST_BEFORE, e, carId.value)
                null
            }
        }

    override suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot> =
        telemetry.span(DataTelemetry.HEALTH_SCORE, OP_RECORD, snapshot.carId.value) {
            try {
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
                    algoVersion = HealthScoreCalculator.RULES_VERSION,
                    computedAt = snapshot.computedAt.toString(),
                    now = now,
                    syncStatus = SyncStatus.PENDING.name,
                )
                requestSync(snapshot.id.value)
                snapshot.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.HEALTH_SCORE, OP_RECORD, e, snapshot.carId.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /**
     * Tell the scheduler there is something worth pushing. Called after the write has
     * committed, and only when it succeeded.
     *
     * Failure to *schedule* never fails the write: the row is safely local and `PENDING`,
     * and the next trigger will carry it.
     */
    private suspend fun requestSync(id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.HEALTH_SCORE, "$OP_RECORD.schedule", e, id)
        }
    }

    private companion object {
        const val OP_LATEST = "latest"
        const val OP_LATEST_BEFORE = "latestOnOrBefore"
        const val OP_RECORD = "record"
    }
}
