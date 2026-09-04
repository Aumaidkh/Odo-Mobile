package com.hopcape.odo.core.domain.benchmark

import com.hopcape.odo.core.domain.shared.Amount

/**
 * What one job normally costs for this car, in this city, at this kind of workshop.
 *
 * A band, never a single number. Workshops differ for reasons that are nobody's fault — a
 * busier bay, a costlier part, an hour more labour — and a point estimate presented at a
 * counter invites an argument the owner cannot win.
 *
 * [scope] and [basis] are what make it defensible. They say how wide the server had to cast
 * to answer and whether real bills or a parts-and-labour calculation produced it, which is
 * exactly what the "How we know" sheet shows.
 */
data class PriceBand(
    val low: Amount,
    val typical: Amount,
    val high: Amount,
    /** Real bills behind it. Zero on a modelled band, which is not a fault. */
    val sampleSize: Int,
    val scope: BenchmarkScope,
    val basis: BenchmarkBasis,
    /** The sum behind a modelled band. Null when real bills produced it. */
    val working: BandWorking? = null,
)

/**
 * How wide the server had to cast to find an answer.
 *
 * Six, matching the ladder the RPC walks (FAIRNESS_SYSTEM_DESIGN §5.4). A screen showing
 * fewer collapses them; the names stay whole here because this is the domain and the loss
 * should happen where it is a display decision.
 */
enum class BenchmarkScope {
    CITY_TIER_SEGMENT,
    CITY_TIER,
    CITY,
    METRO_TIER,
    NATIONAL_TIER,

    /** No pool rows at all — computed from parts and city labour rates. */
    MODELLED,
    ;

    companion object {
        /**
         * The server's own label, or null for one this build does not know.
         *
         * Null is reachable: the ladder can grow a rung before the app is updated, and
         * inventing a scope would put a claim on screen the server never made.
         */
        fun of(label: String?): BenchmarkScope? =
            entries.firstOrNull { it.name.equals(label, ignoreCase = true) }
    }
}

/** Whether real bills or a calculation produced the band. */
enum class BenchmarkBasis {
    /** Bills other owners actually paid. */
    OBSERVED,

    /** Parts plus city labour rate. Honest, and labelled as such wherever it is shown. */
    MODELLED,
    ;

    companion object {
        fun of(label: String?): BenchmarkBasis? =
            entries.firstOrNull { it.name.equals(label, ignoreCase = true) }
    }
}

/**
 * The sum behind a modelled band: parts, plus hours at a rate.
 *
 * Shown, not hidden. A band nobody can check is a number to be taken on faith, and a "wrong
 * price" report is only actionable when the reader can see which input was wrong.
 */
data class BandWorking(
    val partsPaise: Long,
    val labourHours: Double,
    val labourRatePerHour: Amount,
)
