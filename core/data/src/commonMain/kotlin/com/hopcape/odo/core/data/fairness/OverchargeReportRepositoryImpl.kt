package com.hopcape.odo.core.data.fairness

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer

/**
 * Stores an overcharge report locally, PENDING, and leaves it there until something can push
 * it.
 *
 * Written to the database rather than fired at a network call because the server side does
 * not exist yet: a report the owner took the trouble to file must survive that gap. It is a
 * syncable record in its own right — its own identity, its own lifecycle — so it carries the
 * full sync column set and its own `SyncEntity` slot, unlike an entry's categories.
 *
 * This layer owns what an operation means: mapping storage failures to [DomainError],
 * telemetry, id generation, and asking the scheduler for a sync after a committed write.
 * Deriving `owner_id` from the reported service log lives behind [local].
 */
internal class OverchargeReportRepositoryImpl(
    private val local: OverchargeReportLocalDataSource,
    private val telemetry: DataTelemetry,
    private val idGenerator: IdGenerator,
    private val scheduler: SyncScheduler,
    private val runner: SyncRunner<OverchargeReportDto>,
) : OverchargeReportRepository, Syncable {

    override val entity: SyncEntity = SyncEntity.OVERCHARGE_REPORTS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)

    override suspend fun submit(report: OverchargeReport): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.OVERCHARGE, OP_SUBMIT, report.logId.value) {
            try {
                val id = idGenerator.newId()
                if (local.insert(id, report)) {
                    // A filed report is a syncable record of its own, so it asks for a push
                    // like any other local write. Scheduling failure never fails the report
                    // — it is safely local and PENDING.
                    try {
                        scheduler.requestSync(SyncReason.LocalWrite)
                    } catch (e: Exception) {
                        telemetry.crashed(DataTelemetry.OVERCHARGE, "$OP_SUBMIT.schedule", e, report.logId.value)
                    }
                    Unit.right()
                } else {
                    telemetry.failed(DataTelemetry.OVERCHARGE, OP_SUBMIT, DomainError.ServiceLogNotFound, report.logId.value)
                    DomainError.ServiceLogNotFound.left()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.OVERCHARGE, OP_SUBMIT, e, report.logId.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    private companion object {
        const val OP_SUBMIT = "submit"
    }
}
