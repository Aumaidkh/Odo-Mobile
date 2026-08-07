package com.hopcape.odo.core.data.health

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlin.time.Instant

/**
 * [HealthScoreRepository] over a [HealthScoreLocalDataSource] — offline-first. This layer
 * owns what an operation means: mapping storage failures to [DomainError], telemetry, and
 * asking the scheduler for a sync after a committed write. How the rows are read and
 * written lives behind [local].
 *
 * There is no remote data source held yet, unlike the service-log and document
 * repositories. Nothing pushes a score today, and the server recomputes its own snapshots
 * from the rows it receives, so the seam is left for the engine to define rather than
 * guessed at now.
 *
 * Not [Syncable][com.hopcape.odo.core.sync.Syncable] itself — the SQLDelight-backed
 * `SyncRunner` and `HealthScoreSyncTable` it would need live in `:infrastructure:database`,
 * which this module cannot depend on without a cycle. `HealthScoreSyncable`, in that
 * module, wraps the same runner this class used to hold.
 */
internal class HealthScoreRepositoryImpl(
    private val local: HealthScoreLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : HealthScoreRepository {

    override suspend fun latest(carId: CarId): HealthSnapshot? =
        telemetry.span(DataTelemetry.HEALTH_SCORE, OP_LATEST, carId.value) {
            try {
                local.latest(carId)
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
                local.latestOnOrBefore(carId, instant)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.HEALTH_SCORE, OP_LATEST_BEFORE, e, carId.value)
                null
            }
        }

    override fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>> =
        local.observeHistory(carId)
            .catch { cause ->
                telemetry.crashed(DataTelemetry.HEALTH_SCORE, OP_OBSERVE_HISTORY, cause, carId.value)
                // No readable history reads as none: the timeline drops its score events and
                // still renders every service, document and milestone it has.
                emit(emptyList())
            }

    override suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot> =
        telemetry.span(DataTelemetry.HEALTH_SCORE, OP_RECORD, snapshot.carId.value) {
            try {
                local.insert(snapshot)
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
        const val OP_OBSERVE_HISTORY = "observeHistory"
        const val OP_RECORD = "record"
    }
}
