package com.hopcape.odo.core.data.servicelog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * SQLDelight-backed [ServiceLogRepository] — offline-first. The local DB is the source of
 * truth; every write lands `sync_status = PENDING` for the engine that arrives in M5, and
 * nothing here waits on a network call.
 *
 * [remote] is held but not yet driven: pushing and pulling belong to `Syncable.syncWith`,
 * which lands with the engine. It is wired now so the seam exists before there is anything
 * behind it, rather than being retrofitted through a repository that had learned to live
 * without it.
 *
 * Timestamps are client-stamped (offline-first; the server reconciles on sync).
 */
internal class ServiceLogRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    @Suppress("unused") private val remote: ServiceLogRemoteDataSource,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ServiceLogRepository {

    private val queries get() = database.serviceLogQueries

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
        queries.selectByCar(carId.value, ::serviceLogFromRow)
            .asFlow()
            .mapToList(dispatcher)
            .reportingFailures(OP_OBSERVE_CAR, carId.value, empty = emptyList())

    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> =
        queries.selectById(id.value, ::serviceLogFromRow)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .reportingFailures(OP_OBSERVE_ONE, id.value, empty = null)

    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
        telemetry.span(DataTelemetry.SERVICE_LOG, OP_ADD, entry.id.value) {
            try {
                val now = clock.now().toString()
                database.transaction {
                    queries.insertServiceLog(
                        id = entry.id.value,
                        carId = entry.carId.value,
                        ownerId = entry.ownerId.value,
                        serviceDate = entry.serviceDate.toString(),
                        odometerKm = entry.odometer.km.toLong(),
                        totalAmountPaise = entry.totalAmount.paise,
                        workshopName = entry.workshopName?.value,
                        notes = entry.notes?.value,
                        source = entry.source.name,
                        billId = entry.billId?.value,
                        billPhotoPath = entry.billPhotoRef,
                        fairnessSnapshot = entry.fairness?.toJson(),
                        now = now,
                        syncStatus = SyncStatus.PENDING.name,
                    )
                    writeCategories(entry)
                }
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
                val now = clock.now().toString()
                // The existence check shares the write's transaction, so an entry deleted
                // between them cannot turn a ServiceLogNotFound into a silent no-op.
                val result = database.transactionWithResult {
                    if (queries.selectById(entry.id.value, ::serviceLogFromRow).executeAsOneOrNull() == null) {
                        DomainError.ServiceLogNotFound.left()
                    } else {
                        queries.updateServiceLog(
                            serviceDate = entry.serviceDate.toString(),
                            odometerKm = entry.odometer.km.toLong(),
                            totalAmountPaise = entry.totalAmount.paise,
                            workshopName = entry.workshopName?.value,
                            notes = entry.notes?.value,
                            source = entry.source.name,
                            billId = entry.billId?.value,
                            billPhotoPath = entry.billPhotoRef,
                            fairnessSnapshot = entry.fairness?.toJson(),
                            updatedAt = now,
                            // An edited row has to reach the server again.
                            syncStatus = SyncStatus.PENDING.name,
                            id = entry.id.value,
                        )
                        // Categories are rewritten wholesale with their parent. Because the
                        // parent row is always written too, a category-only edit still bumps
                        // `updated_at` and goes PENDING — without that, a tag change would
                        // never sync.
                        writeCategories(entry)
                        entry.right()
                    }
                }
                result
                    .onRight { requestSync(OP_UPDATE, entry.id.value) }
                    .onLeft { telemetry.failed(DataTelemetry.SERVICE_LOG, OP_UPDATE, it, entry.id.value) }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SERVICE_LOG, OP_UPDATE, e, entry.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.SERVICE_LOG, OP_DELETE, id.value) {
            try {
                val now = clock.now().toString()
                // The tombstone stays PENDING so the deletion itself reaches the server;
                // the categories stay with it, since a restored row would need them back.
                queries.softDeleteServiceLog(deletedAt = now, syncStatus = SyncStatus.PENDING.name, id = id.value)
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
                queries.selectOdometerReadings(carId.value, ::odometerReadingFromRow)
                    .executeAsList()
                    .ifEmpty { null }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SERVICE_LOG, OP_READINGS, e, carId.value)
                // Null here would read as "no such car" and reject a legitimate write with
                // CarNotFound. An empty timeline is the safer lie: the ordering check simply
                // has nothing to compare against, and the entry is still validated on its
                // own fields.
                emptyList()
            }
        }

    /** Delete-and-reinsert, always inside the caller's transaction. */
    private fun writeCategories(entry: ServiceLogEntry) {
        queries.deleteCategoriesFor(entry.id.value)
        entry.categories.forEach { category ->
            queries.insertCategory(serviceLogId = entry.id.value, category = category.name)
        }
    }

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
    }
}
