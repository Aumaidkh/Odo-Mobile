package com.hopcape.odo.core.data.reminder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of reminders, as the repository needs it: push what changed locally,
 * pull what changed remotely.
 *
 * A port, declared here in `:core:data` because this is the layer that knows how a row
 * becomes a payload. `:infrastructure:supabase` implements it; the fake below is what a
 * build without that module runs on.
 *
 * The pull matters more here than for most tables: the server engine (M4) writes
 * `sent`/`actioned` status changes onto generated rows, and other devices push custom
 * reminders this one has never seen.
 */
interface ReminderRemoteDataSource {

    /** Reminders changed since [since] (null = never synced, so everything). */
    suspend fun fetchSince(carId: String, since: Instant?): List<ReminderDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(reminders: List<ReminderDto>): List<ReminderDto>
}

/**
 * The wire shape of a reminder — snake_case to match the Postgres columns (DB_SCHEMA
 * §9.8), so the same DTO serves the Supabase REST payload without a second mapping.
 *
 * Enum-ish fields ([reminderType], [status], [repeatKind], [preset]) carry the lowercase
 * Postgres labels; the local columns store the Kotlin constant names, and the sync table
 * converts at the boundary.
 *
 * Nullable fields default to null but are still serialized: PostgREST batch upserts
 * reject rows whose key sets differ (PGRST102), and a cleared field that is omitted
 * rather than sent as null would never clear on the server.
 */
@Serializable
data class ReminderDto(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("reminder_type") val reminderType: String,
    @SerialName("due_date") val dueDate: String,
    @SerialName("status") val status: String,
    @SerialName("title") val title: String? = null,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("starts_on") val startsOn: String? = null,
    @SerialName("remind_at") val remindAt: String? = null,
    @SerialName("repeat_kind") val repeatKind: String? = null,
    @SerialName("repeat_every_days") val repeatEveryDays: Long? = null,
    @SerialName("repeat_every_km") val repeatEveryKm: Long? = null,
    @SerialName("anchor_km") val anchorKm: Long? = null,
    @SerialName("preset") val preset: String? = null,
    @SerialName("dismissed_custom_id") val dismissedCustomId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/**
 * Stand-in when `:infrastructure:supabase` is absent: accepts pushes, remembers nothing,
 * and has nothing to pull.
 *
 * It returns the pushed rows back unchanged, which is honest about what it is — a server
 * that agrees with everything. It deliberately does **not** fabricate a `remote_version`:
 * inventing one would let local rows flip to SYNCED against a server that never saw them,
 * and the first real sync would then skip exactly the rows that were never sent.
 */
internal class FakeReminderRemoteDataSource : ReminderRemoteDataSource {
    override suspend fun fetchSince(carId: String, since: Instant?): List<ReminderDto> = emptyList()
    override suspend fun push(reminders: List<ReminderDto>): List<ReminderDto> = reminders
}
