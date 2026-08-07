package com.hopcape.odo.core.data.reminder

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.repository.ReminderRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [ReminderRepository] over a [ReminderLocalDataSource] — offline-first; the local store
 * is the source of truth. This layer owns what an operation means: mapping storage
 * failures to [DomainError], telemetry, id generation, and asking the scheduler for a
 * sync after a committed write. How the rows are read and written — including dropping a
 * row this build cannot read — lives behind [local].
 *
 * Stores only what cannot be recomputed: the owner's custom reminders and the record of
 * dismissed occurrences. The derived reminders (insurance, PUC, service due) have no
 * rows here — the feed recomputes them from documents and service history on every read.
 */
internal class ReminderRepositoryImpl(
    private val local: ReminderLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val ids: IdGenerator,
    private val owners: CurrentOwnerProvider,
    private val runner: SyncRunner<ReminderDto>,
) : ReminderRepository, Syncable {

    override val entity: SyncEntity = SyncEntity.REMINDERS

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)

    /**
     * Tell the scheduler there is something worth pushing. Called after a write has
     * committed and only when it succeeded. Failure to *schedule* never fails the write:
     * the data is safely local and `PENDING`, and the next trigger will carry it.
     */
    private suspend fun requestSync(operation: String, id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.REMINDER, "$operation.schedule", e, id)
        }
    }

    override fun observeCustom(carId: CarId): Flow<List<CustomReminder>> =
        local.observeCustomByCar(carId).reportingFailures(OP_OBSERVE_CUSTOM, carId.value, empty = emptyList())

    override fun observe(id: ReminderId): Flow<CustomReminder?> =
        local.observeById(id).reportingFailures(OP_OBSERVE_ONE, id.value, empty = null)

    override suspend fun add(reminder: CustomReminder): Either<DomainError, CustomReminder> =
        telemetry.span(DataTelemetry.REMINDER, OP_ADD, reminder.id.value) {
            try {
                local.insert(reminder)
                requestSync(OP_ADD, reminder.id.value)
                reminder.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_ADD, e, reminder.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun update(reminder: CustomReminder): Either<DomainError, CustomReminder> =
        telemetry.span(DataTelemetry.REMINDER, OP_UPDATE, reminder.id.value) {
            try {
                if (local.update(reminder)) {
                    requestSync(OP_UPDATE, reminder.id.value)
                    reminder.right()
                } else {
                    telemetry.failed(DataTelemetry.REMINDER, OP_UPDATE, DomainError.ReminderNotFound, reminder.id.value)
                    DomainError.ReminderNotFound.left()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_UPDATE, e, reminder.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: ReminderId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.REMINDER, OP_DELETE, id.value) {
            try {
                local.softDelete(id)
                requestSync(OP_DELETE, id.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_DELETE, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override fun observeDismissals(carId: CarId): Flow<List<ReminderDismissal>> =
        local.observeDismissals(carId).reportingFailures(OP_OBSERVE_DISMISSALS, carId.value, empty = emptyList())

    override suspend fun dismiss(
        carId: CarId,
        dismissal: ReminderDismissal,
    ): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.REMINDER, OP_DISMISS) {
            try {
                val id = ids.newId()
                local.insertDismissal(id = id, carId = carId, ownerId = owners.currentOwnerId(), dismissal = dismissal)
                requestSync(OP_DISMISS, id)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_DISMISS, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /**
     * A read that throws would otherwise tear down the collecting screen. The stream
     * stays alive on [empty] and the failure is reported, because a reminders list that
     * quietly shows nothing is a bug someone has to be able to see.
     */
    private fun <T> Flow<T>.reportingFailures(operation: String, id: String, empty: T): Flow<T> =
        catch { cause ->
            telemetry.crashed(DataTelemetry.REMINDER, operation, cause, id)
            emit(empty)
        }

    private companion object {
        const val OP_OBSERVE_CUSTOM = "observeCustom"
        const val OP_OBSERVE_ONE = "observeById"
        const val OP_OBSERVE_DISMISSALS = "observeDismissals"
        const val OP_ADD = "add"
        const val OP_UPDATE = "update"
        const val OP_DELETE = "softDelete"
        const val OP_DISMISS = "dismiss"
    }
}
