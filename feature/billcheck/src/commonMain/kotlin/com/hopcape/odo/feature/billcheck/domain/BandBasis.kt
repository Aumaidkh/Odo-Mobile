package com.hopcape.odo.feature.billcheck.domain

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.WorkshopTier

/**
 * Where one line's band came from — the answer to "how do you know".
 *
 * Play requires a way to report a wrong answer and a disclaimer wherever a model contributed
 * (AI_ADVISORY_PLAN §4), and both live on this sheet. It is also the honest half of the
 * feature: a band nobody can interrogate is a number to be taken on faith, and the whole
 * product is about not doing that at a counter.
 */
internal data class BandBasis(
    /** The line this band is for, e.g. "AC service". */
    val lineName: String,
    val low: Amount,
    val high: Amount,
    val city: String,
    /** 1, 2 or 3 — the city tier the labour rate is keyed by. */
    val cityTier: Int,
    val workshop: WorkshopTier,
    /** The vehicle segment in the owner's words, e.g. "1.2L petrol hatchback". */
    val segment: String,
    val labourRatePerHour: Amount,
    val labourHours: Double,
    /** Narrowest first. Exactly one is [RungState.USED]. */
    val rungs: List<Rung>,
)

/**
 * One rung of the benchmark ladder, as the owner sees it.
 *
 * Three rather than the ladder's six (FAIRNESS_SYSTEM_DESIGN §5.4): the six are filter
 * combinations, and naming them here would explain the query rather than the answer.
 */
internal data class Rung(val scope: BandScope, val state: RungState)

internal enum class BandScope { THIS_CAR_THIS_CENTRE, CITY_TIER_SEGMENT, NATIONAL }

internal enum class RungState {
    /** Nothing has collected in this bucket yet. */
    NO_DATA,

    /** The rung the band came from. */
    USED,

    /** Never reached, because a narrower rung answered. */
    NOT_NEEDED,
}
