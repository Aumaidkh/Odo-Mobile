package com.hopcape.odo.core.data.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The source of city benchmarks — the server's `get_fairness_estimate` RPC, which returns
 * only the aggregate average and its sample size. The raw pool behind it is de-identified
 * and never client-readable (DB_SCHEMA §12), which is why this port can only ever ask for
 * averages and never for the rows they came from.
 *
 * Takes category **names** rather than the domain enum: this is the wire boundary, and the
 * server owns the taxonomy. Mapping a name back to a `ServiceCategory` — and dropping one
 * it does not recognise — is the repository's job.
 *
 * `:core:network` implements this when it exists; until then [FakeFairnessRemoteDataSource]
 * keeps the flow walkable.
 */
interface FairnessRemoteDataSource {
    suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto>
}

@Serializable
data class FairnessEstimateDto(
    @SerialName("service_category") val category: String,
    @SerialName("city") val city: String,
    @SerialName("city_average_paise") val cityAveragePaise: Long,
    @SerialName("sample_size") val sampleSize: Int,
    /**
     * The 25th/75th percentile of the pool, which `get_fairness_estimate` already returns
     * (DB_SCHEMA §12.1). Nullable because a benchmark that carries only an average is a
     * legal answer, and a thin pool is exactly where the client needs the percentiles: it
     * is the only honest figure to show when there is no verdict to give.
     */
    @SerialName("p25_paise") val p25Paise: Long? = null,
    @SerialName("p75_paise") val p75Paise: Long? = null,
)

/**
 * Canned benchmarks so the fairness flow can be walked end to end before the RPC exists.
 *
 * The table is the one that used to live inside `:feature:fairness-check`'s sample analyzer,
 * moved here so there is a single set of made-up numbers rather than one per caller — and so
 * deleting them later is one file, not a search.
 *
 * Sample sizes are deliberately realistic rather than flattering: [AC] ships a sample of 3,
 * which lands under the confidence floor and exercises the "show a range, not a verdict"
 * path the PRD demands. Categories absent from the table return nothing at all, which is the
 * other case the UI has to handle.
 */
internal class FakeFairnessRemoteDataSource : FairnessRemoteDataSource {

    override suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto> =
        categories.mapNotNull { name ->
            CANNED[name]?.let { canned ->
                FairnessEstimateDto(
                    category = name,
                    city = city,
                    cityAveragePaise = canned.averageRupees * 100,
                    sampleSize = canned.sampleSize,
                    p25Paise = canned.p25Rupees * 100,
                    p75Paise = canned.p75Rupees * 100,
                )
            }
        }

    /** One made-up benchmark, in rupees, with the spread a real pool would have. */
    private data class Canned(
        val averageRupees: Long,
        val sampleSize: Int,
        val p25Rupees: Long,
        val p75Rupees: Long,
    )

    private companion object {
        val CANNED = mapOf(
            "OIL_CHANGE" to Canned(2_100, 31, 1_800, 2_450),
            "BRAKES" to Canned(3_400, 24, 2_900, 3_950),
            "GENERAL_SERVICE" to Canned(4_200, 48, 3_600, 4_900),
            "BATTERY" to Canned(5_800, 12, 5_200, 6_500),
            "TYRES" to Canned(12_500, 9, 11_000, 14_000),
            // Under the 5-point floor: exercises the low-confidence path, where the range
            // is the only thing the UI can honestly show.
            "AC" to Canned(2_600, 3, 2_200, 3_100),
        )
    }
}
