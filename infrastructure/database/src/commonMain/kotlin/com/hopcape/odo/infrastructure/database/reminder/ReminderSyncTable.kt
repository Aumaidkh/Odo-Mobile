package com.hopcape.odo.infrastructure.database.reminder

import com.hopcape.odo.core.data.reminder.ReminderDto
import com.hopcape.odo.core.data.reminder.ReminderRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Reminders
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `reminders` as the sync algorithm sees it.
 *
 * Pushes carry the client-written rows (custom reminders and dismissals); pulls bring
 * back server-side status changes and rows other devices wrote. Both directions are the
 * standard last-write-wins run — nothing reminder-specific in the algorithm.
 *
 * Two server-side refusals are expected and handled by [markConflict]'s normal path:
 * a project that has not run the `0022_reminders_custom` delta yet rejects every custom
 * row (unknown enum value), and a dismissal can race the server engine onto the dedupe
 * index. Both take the row out of the outbox until a local edit re-queues it; the data
 * stays local and the feed keeps working either way.
 *
 * Enum-ish columns are Kotlin constant names locally and lowercase Postgres labels on
 * the wire.
 */
internal class ReminderSyncTable(
    private val database: OdoDatabase,
    private val remote: ReminderRemoteDataSource,
    private val carId: () -> String?,
) : SyncTable<ReminderDto> {

    private val queries get() = database.reminderQueries

    override fun idOf(dto: ReminderDto): String = dto.id

    override fun updatedAtOf(dto: ReminderDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<ReminderDto> =
        queries.selectPending().executeAsList().map(Reminders::toDto)

    override suspend fun push(rows: List<ReminderDto>): List<ReminderDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    override suspend fun fetch(since: Instant?): List<ReminderDto> {
        val car = carId() ?: return emptyList()
        return remote.fetchSince(car, since)
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: ReminderDto) {
        queries.insertFromRemote(
            id = dto.id,
            car_id = dto.carId,
            owner_id = dto.ownerId,
            reminder_type = dto.reminderType.uppercase(),
            due_date = dto.dueDate,
            status = dto.status.uppercase(),
            title = dto.title,
            is_paused = if (dto.isPaused) 1L else 0L,
            starts_on = dto.startsOn,
            remind_at = dto.remindAt,
            repeat_kind = dto.repeatKind?.uppercase(),
            repeat_every_days = dto.repeatEveryDays,
            repeat_every_km = dto.repeatEveryKm,
            anchor_km = dto.anchorKm,
            preset = dto.preset?.uppercase(),
            dismissed_custom_id = dto.dismissedCustomId,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            car_id = dto.carId,
            owner_id = dto.ownerId,
            reminder_type = dto.reminderType.uppercase(),
            due_date = dto.dueDate,
            status = dto.status.uppercase(),
            title = dto.title,
            is_paused = if (dto.isPaused) 1L else 0L,
            starts_on = dto.startsOn,
            remind_at = dto.remindAt,
            repeat_kind = dto.repeatKind?.uppercase(),
            repeat_every_days = dto.repeatEveryDays,
            repeat_every_km = dto.repeatEveryKm,
            anchor_km = dto.anchorKm,
            preset = dto.preset?.uppercase(),
            dismissed_custom_id = dto.dismissedCustomId,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }
}

private fun Reminders.toDto() = ReminderDto(
    id = id,
    carId = car_id,
    ownerId = owner_id,
    reminderType = reminder_type.lowercase(),
    dueDate = due_date,
    status = status.lowercase(),
    title = title,
    isPaused = is_paused != 0L,
    startsOn = starts_on,
    remindAt = remind_at,
    repeatKind = repeat_kind?.lowercase(),
    repeatEveryDays = repeat_every_days,
    repeatEveryKm = repeat_every_km,
    anchorKm = anchor_km,
    preset = preset?.lowercase(),
    dismissedCustomId = dismissed_custom_id,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
