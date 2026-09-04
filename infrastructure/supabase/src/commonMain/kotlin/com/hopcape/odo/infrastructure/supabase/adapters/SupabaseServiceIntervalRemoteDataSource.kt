package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.schedule.ServiceIntervalDto
import com.hopcape.odo.core.data.schedule.ServiceIntervalRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The service schedule from `service_categories`.
 *
 * A plain table read, not an RPC: the schedule is public reference data with no owner and
 * nothing to de-identify, unlike the bill pool the band comes from.
 *
 * Inactive rows are left out. A category retired from the catalogue should stop answering
 * rather than keep a stale interval alive in every installed app.
 */
internal class SupabaseServiceIntervalRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ServiceIntervalRemoteDataSource {

    override suspend fun intervals(): List<ServiceIntervalDto> =
        postgrest.select(
            table = TABLE,
            serializer = ServiceCategoryRow.serializer(),
            filters = mapOf(COLUMN_ACTIVE to "eq.true"),
        ).map { ServiceIntervalDto(it.slug, it.intervalKm, it.intervalMonths) }

    private companion object {
        const val TABLE = "service_categories"
        const val COLUMN_ACTIVE = "is_active"
    }
}

@Serializable
private data class ServiceCategoryRow(
    @SerialName("slug") val slug: String,
    @SerialName("interval_km") val intervalKm: Int? = null,
    @SerialName("interval_months") val intervalMonths: Int? = null,
)
