package com.hopcape.odo.core.data.reminder

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.data.sync.SyncStatus
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
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [ReminderRepository] — offline-first. The local DB is the source of
 * truth; every write lands `sync_status = PENDING` and nothing here waits on a network
 * call.
 *
 * Stores only what cannot be recomputed: the owner's custom reminders and the record of
 * dismissed occurrences. The derived reminders (insurance, PUC, service due) have no
 * rows here — the feed recomputes them from documents and service history on every read.
 *
 * A row this build cannot read (unknown repeat kind, unparseable date — written by a
 * newer build) is skipped and reported rather than guessed at: a nudge on a guessed
 * cadence is worse than no nudge.
 */
internal class ReminderRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val remote: ReminderRemoteDataSource,
    syncTelemetry: SyncTelemetry,
    private val ids: IdGenerator,
    private val owners: CurrentOwnerProvider,
    carId: () -> String?,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReminderRepository, Syncable {

    private val queries get() = database.reminderQueries

    override val entity: SyncEntity = SyncEntity.REMINDERS

    private val runner = SyncRunner(
        entity = SyncEntity.REMINDERS,
        table = ReminderSyncTable(database = database, remote = remote, carId = carId),
        database = database,
        telemetry = syncTelemetry,
        clock = clock,
    )

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
        queries.selectCustomByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toCustomReminderOrNull() ?: skip(OP_OBSERVE_CUSTOM, it.id) } }
            .reportingFailures(OP_OBSERVE_CUSTOM, carId.value, empty = emptyList())

    override fun observe(id: ReminderId): Flow<CustomReminder?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.let { it.toCustomReminderOrNull() ?: skip(OP_OBSERVE_ONE, it.id) } }
            .reportingFailures(OP_OBSERVE_ONE, id.value, empty = null)

    override suspend fun add(reminder: CustomReminder): Either<DomainError, CustomReminder> =
        telemetry.span(DataTelemetry.REMINDER, OP_ADD, reminder.id.value) {
            try {
                val now = clock.now().toString()
                val cadence = reminder.cadence.toColumns()
                queries.insertReminder(
                    id = reminder.id.value,
                    carId = reminder.carId.value,
                    ownerId = reminder.ownerId.value,
                    dueDate = reminder.startsOn.toString(),
                    title = reminder.title.value,
                    isPaused = if (reminder.paused) 1L else 0L,
                    startsOn = reminder.startsOn.toString(),
                    remindAt = reminder.at.toString(),
                    repeatKind = cadence.repeatKind,
                    repeatEveryDays = cadence.repeatEveryDays,
                    repeatEveryKm = cadence.repeatEveryKm,
                    anchorKm = reminder.anchorKm?.toLong(),
                    preset = reminder.preset?.name,
                    now = now,
                    syncStatus = SyncStatus.PENDING.name,
                )
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
                val now = clock.now().toString()
                // The existence check shares the write's transaction, so a reminder
                // deleted between them cannot turn a ReminderNotFound into a silent no-op.
                val result = database.transactionWithResult {
                    if (queries.selectLiveId(reminder.id.value).executeAsOneOrNull() == null) {
                        DomainError.ReminderNotFound.left()
                    } else {
                        val cadence = reminder.cadence.toColumns()
                        queries.updateReminder(
                            dueDate = reminder.startsOn.toString(),
                            title = reminder.title.value,
                            isPaused = if (reminder.paused) 1L else 0L,
                            startsOn = reminder.startsOn.toString(),
                            remindAt = reminder.at.toString(),
                            repeatKind = cadence.repeatKind,
                            repeatEveryDays = cadence.repeatEveryDays,
                            repeatEveryKm = cadence.repeatEveryKm,
                            anchorKm = reminder.anchorKm?.toLong(),
                            preset = reminder.preset?.name,
                            updatedAt = now,
                            // An edited row has to reach the server again.
                            syncStatus = SyncStatus.PENDING.name,
                            id = reminder.id.value,
                        )
                        reminder.right()
                    }
                }
                result
                    .onRight { requestSync(OP_UPDATE, reminder.id.value) }
                    .onLeft { telemetry.failed(DataTelemetry.REMINDER, OP_UPDATE, it, reminder.id.value) }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_UPDATE, e, reminder.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: ReminderId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.REMINDER, OP_DELETE, id.value) {
            try {
                val now = clock.now().toString()
                // The tombstone stays PENDING so the deletion itself reaches the server.
                queries.softDeleteReminder(
                    deletedAt = now,
                    syncStatus = SyncStatus.PENDING.name,
                    id = id.value,
                )
                requestSync(OP_DELETE, id.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_DELETE, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override fun observeDismissals(carId: CarId): Flow<List<ReminderDismissal>> =
        queries.selectDismissalsByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toDismissalOrNull() } }
            .reportingFailures(OP_OBSERVE_DISMISSALS, carId.value, empty = emptyList())

    override suspend fun dismiss(
        carId: CarId,
        dismissal: ReminderDismissal,
    ): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.REMINDER, OP_DISMISS) {
            try {
                val now = clock.now().toString()
                val id = ids.newId()
                // OR IGNORE + the dismissal unique indexes: dismissing the same occurrence
                // twice finds the first row already there and writes nothing.
                queries.insertDismissal(
                    id = id,
                    carId = carId.value,
                    ownerId = owners.currentOwnerId().value,
                    reminderType = dismissal.kind.name,
                    dueDate = dismissal.dueOn.toString(),
                    dismissedCustomId = dismissal.customId?.value,
                    now = now,
                    syncStatus = SyncStatus.PENDING.name,
                )
                requestSync(OP_DISMISS, id)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.REMINDER, OP_DISMISS, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /** Reports a row this build cannot read, then drops it. Always answers `null`. */
    private suspend fun skip(operation: String, id: String): CustomReminder? {
        telemetry.failed(DataTelemetry.REMINDER, operation, UnreadableRow, id)
        return null
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

    /** Named so the dashboard reads `error=UnreadableRow`, not a raw string. */
    private object UnreadableRow

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
