package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.servicelog.ServiceLogDto
import com.hopcape.odo.core.data.servicelog.ServiceLogRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `service_logs` over PostgREST, plus the `service_log_categories` rows that hang off it.
 *
 * The delta pull is `car_id = ? AND updated_at > cursor`, ordered by `updated_at` so the
 * cursor advances monotonically. Soft-deleted rows are **not** filtered out: a tombstone is
 * the only way the device learns an entry was deleted elsewhere (SYNC_DESIGN §6).
 *
 * **Categories travel with their parent, in a second statement.** They are a projection of
 * the entry with no identity of their own, which is why they are not a [SyncEntity] and have
 * no cursor — but they do live in their own table server-side, so a push is two writes. The
 * parent goes first: a category row referencing an entry the server has not accepted is a
 * foreign-key error.
 */
internal class SupabaseServiceLogRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ServiceLogRemoteDataSource {

    override suspend fun fetchSince(carId: String, since: Instant?): List<ServiceLogDto> {
        val entries = postgrest.select(
            table = TABLE,
            serializer = ServiceLogRow.serializer(),
            filters = buildMap {
                put(COLUMN_CAR_ID, "eq.$carId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        ).map(ServiceLogRow::toDto)
        if (entries.isEmpty()) return entries

        // One request for every entry's categories rather than one per entry. `in.(…)` is
        // PostgREST's IN clause; the page is already bounded, so the URL cannot run away.
        val ids = entries.joinToString(",") { it.id }
        val categories = postgrest.select(
            table = CATEGORIES_TABLE,
            serializer = CategoryRow.serializer(),
            filters = mapOf(COLUMN_SERVICE_LOG_ID to "in.($ids)"),
        ).groupBy(CategoryRow::serviceLogId)

        return entries.map { entry ->
            entry.copy(categories = categories[entry.id]?.map(CategoryRow::category).orEmpty())
        }
    }

    override suspend fun push(entries: List<ServiceLogDto>): List<ServiceLogDto> {
        val stored = postgrest.upsert(
            table = TABLE,
            serializer = ServiceLogRow.serializer(),
            rows = entries.map(ServiceLogDto::toRow),
        ).map(ServiceLogRow::toDto)
        if (stored.isEmpty()) return stored

        // Only after the parents are accepted. An entry with no categories writes nothing
        // here — clearing tags that were removed locally is a delete this port has no shape
        // for, and is owed alongside the first UI that can remove one.
        val categoryRows = entries.flatMap { entry ->
            entry.categories.map { CategoryRow(serviceLogId = entry.id, category = it) }
        }
        if (categoryRows.isNotEmpty()) {
            postgrest.upsert(
                table = CATEGORIES_TABLE,
                serializer = CategoryRow.serializer(),
                rows = categoryRows,
                // Nothing reads these back — the categories the caller pushed are already
                // the newest version, and asking for a representation would only add a
                // payload to parse.
                returnRows = false,
            )
        }

        // The server's rows carry its `updated_at`, which is what the local row's
        // `remote_version` is set from. Categories are put back because the caller pushed
        // them and the parent upsert deliberately stripped them.
        val byId = entries.associateBy(ServiceLogDto::id)
        return stored.map { it.copy(categories = byId[it.id]?.categories.orEmpty()) }
    }

    private companion object {
        const val TABLE = "service_logs"
        const val CATEGORIES_TABLE = "service_log_categories"
        const val COLUMN_CAR_ID = "car_id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_SERVICE_LOG_ID = "service_log_id"
    }
}

/**
 * The columns `service_logs` actually has.
 *
 * A separate type from [ServiceLogDto] for one reason: the DTO carries `categories`, which
 * lives in its own table. The codec has `encodeDefaults = true`, so sending the DTO straight
 * would put `"categories": []` in the payload even for an entry with none, and PostgREST
 * rejects the whole upsert with `PGRST204: column not found`. Everything else the DTO holds
 * — including `bill_photo_path` and `fairness_snapshot` — is a real column and goes as-is.
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
    @SerialName("bill_photo_path") val billPhotoPath: String? = null,
    @SerialName("fairness_snapshot") val fairnessSnapshot: String? = null,
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
    billPhotoPath = billPhotoPath,
    fairnessSnapshot = fairnessSnapshot,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/** Categories are filled in separately, from the junction table. */
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
    billPhotoPath = billPhotoPath,
    fairnessSnapshot = fairnessSnapshot,
    categories = emptyList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * A row of `service_log_categories`.
 *
 * `owner_id` is absent on purpose: the server stamps it from the parent entry by trigger, so
 * a client that sent one would have it overwritten anyway (DB_SCHEMA §10.2).
 */
@Serializable
private data class CategoryRow(
    @SerialName("service_log_id") val serviceLogId: String,
    @SerialName("category") val category: String,
)
