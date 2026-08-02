package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `service_logs` over PostgREST.
 *
 * The delta pull is `car_id = ? AND updated_at > cursor`, ordered by `updated_at` so the
 * cursor advances monotonically. Soft-deleted rows are **not** filtered out: a tombstone is
 * the only way the device learns an entry was deleted elsewhere (SYNC_DESIGN §6).
 */
internal class SupabaseServiceLogRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ServiceLogRemoteDataSource {

    override suspend fun fetchSince(carId: String, since: Instant?): List<ServiceLogDto> =
        postgrest.select(
            table = TABLE,
            serializer = ServiceLogRow.serializer(),
            filters = buildMap {
                put(COLUMN_CAR_ID, "eq.$carId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        ).map(ServiceLogRow::toDto)

    override suspend fun push(entries: List<ServiceLogDto>): List<ServiceLogDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = ServiceLogRow.serializer(),
            rows = entries.map(ServiceLogDto::toRow),
        ).map(ServiceLogRow::toDto)

    private companion object {
        const val TABLE = "service_logs"
        const val COLUMN_CAR_ID = "car_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}

/**
 * The columns `service_logs` actually has (DB_SCHEMA §9.4).
 *
 * A separate type from [ServiceLogDto] because the DTO carries three fields the table does
 * not: `bill_photo_path` and `fairness_snapshot` are local columns, and `categories` is a
 * projection with no server home at all — DB_SCHEMA has no `service_log_categories` junction,
 * only `bill_line_items.service_category_id`. Sending them would fail the whole upsert with
 * PostgREST's `PGRST204: column not found`, so they are dropped here rather than silently
 * corrupting a push.
 *
 * That is a real gap, not a design: until the schema gains a home for them, a pulled entry
 * comes back with no categories and no fairness verdict, and a pushed one leaves them behind.
 * Settle it in `docs/DB_SCHEMA.md` before wiring sync against a live project.
 */
@Serializable
private data class ServiceLogRow(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("service_date") val serviceDate: String,
    @SerialName("odometer_km") val odometerKm: Int,
    @SerialName("total_amount_paise") val totalAmountPaise: Long,
    @SerialName("workshop_name") val workshopName: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("source") val source: String,
    @SerialName("bill_id") val billId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

private fun ServiceLogDto.toRow() = ServiceLogRow(
    id = id,
    carId = carId,
    ownerId = ownerId,
    serviceDate = serviceDate,
    odometerKm = odometerKm,
    totalAmountPaise = totalAmountPaise,
    workshopName = workshopName,
    notes = notes,
    source = source,
    billId = billId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun ServiceLogRow.toDto() = ServiceLogDto(
    id = id,
    carId = carId,
    ownerId = ownerId,
    serviceDate = serviceDate,
    odometerKm = odometerKm,
    totalAmountPaise = totalAmountPaise,
    workshopName = workshopName,
    notes = notes,
    source = source,
    billId = billId,
    // Not columns on `service_logs` — see the note on ServiceLogRow.
    billPhotoPath = null,
    fairnessSnapshot = null,
    categories = emptyList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
