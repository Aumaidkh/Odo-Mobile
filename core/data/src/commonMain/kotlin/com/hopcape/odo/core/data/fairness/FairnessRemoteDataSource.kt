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
internal interface FairnessRemoteDataSource {
    suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto>
}

@Serializable
internal data class FairnessEstimateDto(
    @SerialName("service_category") val category: String,
    @SerialName("city") val city: String,
    @SerialName("city_average_paise") val cityAveragePaise: Long,
    @SerialName("sample_size") val sampleSize: Int,
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
            CANNED[name]?.let { (averageRupees, sampleSize) ->
                FairnessEstimateDto(
                    category = name,
                    city = city,
                    cityAveragePaise = averageRupees * 100,
                    sampleSize = sampleSize,
                )
            }
        }

    private companion object {
        /** category name → (city average in rupees, sample size). */
        val CANNED = mapOf(
            "OIL_CHANGE" to (2_100L to 31),
            "BRAKES" to (3_400L to 24),
            "GENERAL_SERVICE" to (4_200L to 48),
            "BATTERY" to (5_800L to 12),
            "TYRES" to (12_500L to 9),
            // Under the 5-point floor: exercises the low-confidence path.
            "AC" to (2_600L to 3),
        )
    }
}
