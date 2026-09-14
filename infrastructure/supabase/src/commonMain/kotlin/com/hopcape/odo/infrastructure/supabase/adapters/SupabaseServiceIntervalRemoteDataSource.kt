package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.schedule.ServiceIntervalDto
import com.hopcape.odo.core.data.schedule.ServiceIntervalRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The service schedule from `service_schedule`.
 *
 * A plain table read, not an RPC: the schedule is public reference data with no owner and
 * nothing to de-identify, unlike the bill pool the band comes from.
 *
 * Not `service_categories.interval_km`, which 20260902200000_reference_data.sql marks
 * SUPERSEDED — it has no brand axis and no display name, and the entered data goes here.
 *
 * Draft rows are left out. The read policy is deliberately unfiltered on status so a row that
 * loses approval is still visible to the panel that has to fix it; picking approved rows is
 * this query's job.
 */
internal class SupabaseServiceIntervalRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ServiceIntervalRemoteDataSource {

    override suspend fun schedule(): List<ServiceIntervalDto> =
        postgrest.select(
            table = TABLE,
            serializer = ServiceScheduleRow.serializer(),
            filters = mapOf(COLUMN_STATUS to "eq.$STATUS_APPROVED"),
        ).map { ServiceIntervalDto(it.brand, it.itemSlug, it.displayName, it.dueKm, it.dueMonths) }

    private companion object {
        const val TABLE = "service_schedule"
        const val COLUMN_STATUS = "status"
        const val STATUS_APPROVED = "approved"
    }
}

@Serializable
private data class ServiceScheduleRow(
    @SerialName("brand") val brand: String? = null,
    @SerialName("item_slug") val itemSlug: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("due_km") val dueKm: Int? = null,
    @SerialName("due_months") val dueMonths: Int? = null,
)
