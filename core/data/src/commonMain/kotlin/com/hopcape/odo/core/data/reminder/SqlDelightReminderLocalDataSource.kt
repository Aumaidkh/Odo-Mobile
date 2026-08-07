package com.hopcape.odo.core.data.reminder

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [ReminderLocalDataSource] — fully offline. The local DB is the source
 * of truth; every write stamps `updated_at` and leaves the row `sync_status = PENDING`
 * for the sync engine.
 *
 * Takes [telemetry] — unlike the other local data sources — purely to report a row it
 * cannot read before dropping it (see [skip]). A guessed cadence is worse than no
 * reminder, so the row is never surfaced instead; the report is what makes that silent
 * drop visible to anyone reading the dashboard.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 */
internal class SqlDelightReminderLocalDataSource(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReminderLocalDataSource {

    private val queries get() = database.reminderQueries

    override suspend fun insert(reminder: CustomReminder) {
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
    }

    override suspend fun update(reminder: CustomReminder): Boolean {
        val now = clock.now().toString()
        return database.transactionWithResult {
            if (queries.selectLiveId(reminder.id.value).executeAsOneOrNull() == null) {
                return@transactionWithResult false
            }
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
            true
        }
    }

    override suspend fun softDelete(id: ReminderId) {
        val now = clock.now().toString()
        // The tombstone stays PENDING so the deletion itself reaches the server.
        queries.softDeleteReminder(deletedAt = now, syncStatus = SyncStatus.PENDING.name, id = id.value)
    }

    override fun observeCustomByCar(carId: CarId): Flow<List<CustomReminder>> =
        queries.selectCustomByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toCustomReminderOrNull() ?: skip(OP_OBSERVE_CUSTOM, it.id) } }

    override fun observeById(id: ReminderId): Flow<CustomReminder?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.let { it.toCustomReminderOrNull() ?: skip(OP_OBSERVE_ONE, it.id) } }

    override fun observeDismissals(carId: CarId): Flow<List<ReminderDismissal>> =
        queries.selectDismissalsByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.mapNotNull { it.toDismissalOrNull() } }

    override suspend fun insertDismissal(id: String, carId: CarId, ownerId: OwnerId, dismissal: ReminderDismissal) {
        val now = clock.now().toString()
        queries.insertDismissal(
            id = id,
            carId = carId.value,
            ownerId = ownerId.value,
            reminderType = dismissal.kind.name,
            dueDate = dismissal.dueOn.toString(),
            dismissedCustomId = dismissal.customId?.value,
            now = now,
            syncStatus = SyncStatus.PENDING.name,
        )
    }

    /** Reports a row this build cannot read, then drops it. Always answers `null`. */
    private suspend fun skip(operation: String, id: String): CustomReminder? {
        telemetry.failed(DataTelemetry.REMINDER, operation, UnreadableRow, id)
        return null
    }

    /** Named so the dashboard reads `error=UnreadableRow`, not a raw string. */
    private object UnreadableRow

    private companion object {
        const val OP_OBSERVE_CUSTOM = "observeCustom"
        const val OP_OBSERVE_ONE = "observeById"
    }
}
