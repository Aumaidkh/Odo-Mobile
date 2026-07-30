package com.hopcape.odo.core.data.fairness

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.servicelog.serviceLogFromRow
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Stores an overcharge report locally, PENDING, and leaves it there until something can push
 * it.
 *
 * Written to the database rather than fired at a network call because the server side does
 * not exist yet: a report the owner took the trouble to file must survive that gap. It is a
 * syncable record in its own right — its own identity, its own lifecycle — so it carries the
 * full sync column set and its own `SyncEntity` slot, unlike an entry's categories.
 *
 * `owner_id` is taken from the parent service log rather than from the caller, mirroring the
 * server's `BEFORE INSERT` trigger (DB_SCHEMA §4): ownership is derived from the record being
 * reported, never asserted by the client.
 */
internal class OverchargeReportRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val idGenerator: IdGenerator,
    private val scheduler: SyncScheduler,
    @Suppress("unused") private val remote: OverchargeRemoteDataSource,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OverchargeReportRepository {

    override suspend fun submit(report: OverchargeReport): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.OVERCHARGE, OP_SUBMIT, report.logId.value) {
            withContext(dispatcher) {
                try {
                    val now = clock.now().toString()
                    val result = database.transactionWithResult {
                        val entry = database.serviceLogQueries
                            .selectById(report.logId.value, ::serviceLogFromRow)
                            .executeAsOneOrNull()

                        if (entry == null) {
                            DomainError.ServiceLogNotFound.left()
                        } else {
                            database.overchargeReportQueries.insertOverchargeReport(
                                id = idGenerator.newId(),
                                serviceLogId = report.logId.value,
                                ownerId = entry.ownerId.value,
                                reason = report.reason.name,
                                category = report.category?.name,
                                note = report.note,
                                now = now,
                                syncStatus = SyncStatus.PENDING.name,
                            )
                            Unit.right()
                        }
                    }
                    result
                        .onRight {
                            // A filed report is a syncable record of its own, so it asks for
                            // a push like any other local write. Scheduling failure never
                            // fails the report — it is safely local and PENDING.
                            try {
                                scheduler.requestSync(SyncReason.LocalWrite)
                            } catch (e: Exception) {
                                telemetry.crashed(DataTelemetry.OVERCHARGE, "$OP_SUBMIT.schedule", e, report.logId.value)
                            }
                        }
                        .onLeft { telemetry.failed(DataTelemetry.OVERCHARGE, OP_SUBMIT, it, report.logId.value) }
                } catch (e: Exception) {
                    telemetry.crashed(DataTelemetry.OVERCHARGE, OP_SUBMIT, e, report.logId.value)
                    DomainError.PersistenceFailure(e.message).left()
                }
            }
        }

    private companion object {
        const val OP_SUBMIT = "submit"
    }
}
