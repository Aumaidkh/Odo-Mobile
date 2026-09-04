package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.benchmark.PriceBandDto
import com.hopcape.odo.core.data.benchmark.PriceBandRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The price band through the `get_fairness_benchmark` RPC.
 *
 * The function walks outward — this car and centre, then the city tier, then the country —
 * and reports which rung answered, so the widening is the server's job rather than five calls
 * from here (FAIRNESS_SYSTEM_DESIGN §5.4). Its last rung computes from parts and labour
 * rates, which is why it answers for jobs the pool has never seen.
 *
 * Nulls are sent explicitly rather than omitted. PostgREST resolves an RPC by the arguments
 * present, and a call that leaves them out asks for a different function signature than the
 * one deployed.
 */
internal class SupabasePriceBandRemoteDataSource(
    private val postgrest: PostgrestClient,
) : PriceBandRemoteDataSource {

    override suspend fun band(
        categorySlug: String,
        city: String,
        segment: String?,
        fuel: String?,
        workshopTier: String?,
    ): PriceBandDto? {
        val rows = postgrest.rpc(
            function = FUNCTION,
            params = JsonObject(
                mapOf(
                    PARAM_CATEGORY to JsonPrimitive(categorySlug),
                    PARAM_CITY to JsonPrimitive(city),
                    PARAM_SEGMENT to segment.orNull(),
                    PARAM_FUEL to fuel.orNull(),
                    PARAM_TIER to workshopTier.orNull(),
                ),
            ),
            serializer = ListSerializer(BenchmarkRow.serializer()),
        )
        // No rows at all is the honest "nothing for this job". A row whose average is null is
        // the same thing said differently, and neither is a band of zero.
        val row = rows.firstOrNull() ?: return null
        if (row.avgPaise == null || row.p25 == null || row.p75 == null) return null

        return PriceBandDto(
            avgPaise = row.avgPaise,
            p25Paise = row.p25,
            p75Paise = row.p75,
            sampleSize = row.sampleSize.toInt(),
            scope = row.scope,
            basis = row.basis,
            partsPaise = row.partsPaise,
            labourHours = row.labourHours,
            labourPaisePerHour = row.labourPaisePerHour,
        )
    }

    private fun String?.orNull() = this?.let { JsonPrimitive(it) } ?: JsonNull

    private companion object {
        const val FUNCTION = "get_fairness_benchmark"
        const val PARAM_CATEGORY = "p_category"
        const val PARAM_CITY = "p_city"
        const val PARAM_SEGMENT = "p_segment"
        const val PARAM_FUEL = "p_fuel"
        const val PARAM_TIER = "p_tier"
    }
}

/** One row of the RPC's `RETURNS TABLE`. The working columns are null on an observed band. */
@Serializable
private data class BenchmarkRow(
    @SerialName("avg_paise") val avgPaise: Long? = null,
    @SerialName("p25") val p25: Long? = null,
    @SerialName("p75") val p75: Long? = null,
    @SerialName("sample_size") val sampleSize: Long = 0,
    @SerialName("scope") val scope: String? = null,
    @SerialName("basis") val basis: String? = null,
    @SerialName("parts_paise") val partsPaise: Long? = null,
    @SerialName("labour_hours") val labourHours: Double? = null,
    @SerialName("labour_paise_per_hour") val labourPaisePerHour: Long? = null,
)
