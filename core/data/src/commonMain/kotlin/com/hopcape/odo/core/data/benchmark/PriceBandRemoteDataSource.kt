package com.hopcape.odo.core.data.benchmark

/**
 * The server side of the price band — the `get_fairness_benchmark` RPC.
 *
 * Not a table read, and it cannot be one: `fairness_data_points` is de-identified and has no
 * client-readable policy at all, so an aggregate reaches the app only through a
 * `SECURITY DEFINER` function (DB_SCHEMA §12.1).
 */
interface PriceBandRemoteDataSource {

    /**
     * The band for one job, or null when the server has nothing for it.
     *
     * Every argument but the first two may be null, and the server widens its search rather
     * than refusing — a car whose segment could not be worked out still gets a city answer.
     */
    suspend fun band(
        categorySlug: String,
        city: String,
        segment: String?,
        fuel: String?,
        workshopTier: String?,
    ): PriceBandDto?
}

/**
 * One row of the RPC's `RETURNS TABLE`.
 *
 * The three `*_paise` working columns are null on an observed band — there is no sum behind
 * bills people actually paid — and populated on a modelled one.
 */
data class PriceBandDto(
    val avgPaise: Long,
    val p25Paise: Long,
    val p75Paise: Long,
    val sampleSize: Int,
    val scope: String?,
    val basis: String?,
    val partsPaise: Long?,
    val labourHours: Double?,
    val labourPaisePerHour: Long?,
)

/**
 * Answers nothing, always.
 *
 * The honest stand-in for a build with no Supabase credentials: every price claim then has no
 * band, which is what the screen already knows how to say. A fake that invented a band would
 * put a made-up rupee figure in front of an owner at a counter.
 */
internal class FakePriceBandRemoteDataSource : PriceBandRemoteDataSource {
    override suspend fun band(
        categorySlug: String,
        city: String,
        segment: String?,
        fuel: String?,
        workshopTier: String?,
    ): PriceBandDto? = null
}
