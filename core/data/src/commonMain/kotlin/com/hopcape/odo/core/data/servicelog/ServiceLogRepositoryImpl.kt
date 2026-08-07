package com.hopcape.odo.core.data.servicelog

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [ServiceLogRepository] over a [ServiceLogLocalDataSource] — offline-first; the local
 * store is the source of truth. This layer owns what an operation means: mapping storage
 * failures to [DomainError], telemetry, and asking the scheduler for a sync after a
 * committed write. How the rows and their categories are read and written lives behind
 * [local].
 */
internal class ServiceLogRepositoryImpl(
    private val local: ServiceLogLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val runner: SyncRunner<ServiceLogDto>,
) : ServiceLogRepository, Syncable {

    override val entity: SyncEntity = SyncEntity.SERVICE_LOGS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)

    /**
     * Tell the scheduler there is something worth pushing. Called after a write has
     * committed — never before, or a sync would go looking for a row that is not there yet
     * — and only when it succeeded, since a failed write left nothing to push.
     *
     * Every write asks, without checking whether one is already queued: coalescing a burst
     * of edits into one run is the scheduler's job ([SyncReason.LocalWrite] is debounced),
     * and a repository that tried to be clever about it would just be a second, worse
     * scheduler.
     *
     * Failure to *schedule* never fails the write. The data is safely local and `PENDING`;
     * the next trigger — app foreground, another edit — will carry it.
     */
    private suspend fun requestSync(operation: String, id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.SERVICE_LOG, "$operation.schedule", e, id)
        }
    }

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> =
        local.observeByCar(carId).reportingFailures(OP_OBSERVE_CAR, carId.value, empty = emptyList())

    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> =
        local.observeById(id).reportingFailures(OP_OBSERVE_ONE, id.value, empty = null)

    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
        telemetry.span(DataTelemetry.SERVICE_LOG, OP_ADD, entry.id.value) {
            try {
                local.insert(entry)
                requestSync(OP_ADD, entry.id.value)
                entry.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SERVICE_LOG, OP_ADD, e, entry.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
        telemetry.span(DataTelemetry.SERVICE_LOG, OP_UPDATE, entry.id.value) {
            try {
                if (local.update(entry)) {
                    requestSync(OP_UPDATE, entry.id.value)
                    entry.right()
                } else {
                    telemetry.failed(DataTelemetry.SERVICE_LOG, OP_UPDATE, DomainError.ServiceLogNotFound, entry.id.value)
                    DomainError.ServiceLogNotFound.left()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SERVICE_LOG, OP_UPDATE, e, entry.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.SERVICE_LOG, OP_DELETE, id.value) {
            try {
                local.softDelete(id)
                // The tombstone is the whole payload of a deletion: without this ask, the
                // row sits PENDING forever and the other device never learns it went.
                requestSync(OP_DELETE, id.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SERVICE_LOG, OP_DELETE, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? =
        telemetry.span(DataTelemetry.SERVICE_LOG, OP_READINGS, carId.value) {
            try {
                // An empty result means no live car contributed its baseline — the car does
                // not exist for this owner, which the domain reads as CarNotFound.
                local.odometerReadings(carId).ifEmpty { null }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SERVICE_LOG, OP_READINGS, e, carId.value)
                // Null here would read as "no such car" and reject a legitimate write with
                // CarNotFound. An empty timeline is the safer lie: the ordering check simply
                // has nothing to compare against, and the entry is still validated on its
                // own fields.
                emptyList()
            }
        }

    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> =
        local.observeOdometerReadings(carId)
            .reportingFailures(OP_OBSERVE_READINGS, carId.value, empty = emptyList())

    /**
     * A read that throws would otherwise tear down the collecting screen. The stream stays
     * alive on [empty] and the failure is reported, because a list that quietly shows
     * nothing is a bug someone has to be able to see.
     */
    private fun <T> Flow<T>.reportingFailures(operation: String, id: String, empty: T): Flow<T> =
        catch { cause ->
            telemetry.crashed(DataTelemetry.SERVICE_LOG, operation, cause, id)
            emit(empty)
        }

    private companion object {
        const val OP_OBSERVE_CAR = "observeByCar"
        const val OP_OBSERVE_ONE = "observeById"
        const val OP_ADD = "add"
        const val OP_UPDATE = "update"
        const val OP_DELETE = "softDelete"
        const val OP_READINGS = "odometerReadings"
        const val OP_OBSERVE_READINGS = "observeOdometerReadings"
    }
}
