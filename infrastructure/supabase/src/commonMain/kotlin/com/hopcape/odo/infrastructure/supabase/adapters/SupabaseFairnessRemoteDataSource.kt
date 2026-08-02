package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.fairness.FairnessEstimateDto
import com.hopcape.odo.core.data.fairness.FairnessRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * City benchmarks through the `get_fairness_estimate` RPC.
 *
 * Not a table read, and it cannot be one: `fairness_data_points` is de-identified and has no
 * client-readable RLS policy at all, so the aggregate reaches the app only through a
 * `SECURITY DEFINER` function (DB_SCHEMA §12.1). That is also why this port can never ask for
 * the rows behind an average.
 *
 * The RPC answers about one category, so a request for several fans out into one call each,
 * concurrently. A category the pool has nothing for is dropped rather than reported as zero —
 * "no benchmark" and "the benchmark is ₹0" are very different things to show an owner.
 *
 * > **Contract gap, unresolved.** DB_SCHEMA §12.1 declares
 * > `get_fairness_estimate(p_category uuid, p_city uuid, p_fuel fuel_type)`, but the client
 * > holds neither lookup id — the port deals in category and city *names*, because the server
 * > owns the taxonomy — and the port carries no fuel type at all. This adapter therefore calls
 * > the function with text names and no fuel. Before the RPC is deployed, either it takes text
 * > and resolves the lookups itself, or the port has to start carrying ids and a fuel type.
 * > That decision is owed; nothing here can settle it.
 */
internal class SupabaseFairnessRemoteDataSource(
    private val postgrest: PostgrestClient,
) : FairnessRemoteDataSource {

    override suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto> =
        coroutineScope {
            categories
                .map { category -> async { estimate(category, city) } }
                .awaitAll()
                .filterNotNull()
        }

    /** One benchmark, or null when the pool holds nothing for this category and city. */
    private suspend fun estimate(category: String, city: String): FairnessEstimateDto? {
        val rows = postgrest.rpc(
            function = FUNCTION,
            params = JsonObject(
                mapOf(
                    PARAM_CATEGORY to JsonPrimitive(category),
                    PARAM_CITY to JsonPrimitive(city),
                ),
            ),
            serializer = ListSerializer(FairnessEstimateRow.serializer()),
        )
        // `RETURNS TABLE` with no matching data points still answers with one row of nulls,
        // which is a benchmark of nothing rather than a benchmark of zero.
        val row = rows.firstOrNull() ?: return null
        if (row.avgPaise == null || row.sampleSize == 0L) return null

        return FairnessEstimateDto(
            category = category,
            city = city,
            cityAveragePaise = row.avgPaise,
            sampleSize = row.sampleSize.toInt(),
            p25Paise = row.p25,
            p75Paise = row.p75,
        )
    }

    private companion object {
        const val FUNCTION = "get_fairness_estimate"
        const val PARAM_CATEGORY = "p_category"
        const val PARAM_CITY = "p_city"
    }
}

/** One row of `get_fairness_estimate`'s `RETURNS TABLE (avg_paise, sample_size, p25, p75)`. */
@Serializable
private data class FairnessEstimateRow(
    @SerialName("avg_paise") val avgPaise: Long? = null,
    @SerialName("sample_size") val sampleSize: Long = 0,
    @SerialName("p25") val p25: Long? = null,
    @SerialName("p75") val p75: Long? = null,
)
