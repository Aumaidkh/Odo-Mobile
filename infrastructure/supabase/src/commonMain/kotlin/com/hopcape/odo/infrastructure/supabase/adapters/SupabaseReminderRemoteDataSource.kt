package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.reminder.ReminderDto
import com.hopcape.odo.core.data.reminder.ReminderRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `reminders` over PostgREST.
 *
 * [ReminderDto] matches the table column for column (DB_SCHEMA §9.8), so it goes over
 * the wire as-is — no intermediate row type.
 *
 * Requires the `0022_reminders_custom` delta on the server: without it every custom row
 * is refused on the unknown enum value, which the sync run reports and marks CONFLICT —
 * an expected state on a project that has not been migrated yet, not a client bug.
 */
internal class SupabaseReminderRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ReminderRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<ReminderDto> =
        postgrest.select(
            table = TABLE,
            serializer = ReminderDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(reminders: List<ReminderDto>): List<ReminderDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = ReminderDto.serializer(),
            rows = reminders,
        )

    private companion object {
        const val TABLE = "reminders"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
