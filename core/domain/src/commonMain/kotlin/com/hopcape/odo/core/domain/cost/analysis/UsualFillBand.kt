package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.shared.Amount

/**
 * What this owner normally spends on a tank.
 *
 * Detection needs it. A payment of ₹300 at a fuel brand is more likely to be a bottle of
 * water at the pump's shop than a fill, and the only way to tell without asking is to
 * compare it with what the same owner's fills usually cost. Someone who tops up ₹200 at a
 * time should not be interrogated about it, and someone who always fills ₹2,000 should be.
 *
 * The band is the middle half of their past fills — the 25th to the 75th percentile — so a
 * single unusual visit does not widen it. [contains] is the question detection actually
 * asks; the two ends are exposed because the screen shows them ("your usual fill is
 * ₹1,800–2,200").
 */
object UsualFillBand {

    /**
     * How many past fills are needed before a band means anything.
     *
     * Below this, one visit moves both ends, and a band built from two fills would flag the
     * third as unusual for no reason. Detection treats "no band" as "do not question the
     * amount", which is the safe direction: it confirms rather than interrogates.
     */
    const val MIN_FILLS: Int = 4

    /**
     * How far below the band an amount has to fall before it is worth asking about.
     *
     * Half the low end. A fill a little under the usual is just a smaller top-up; a fill
     * that is a fraction of it is the shop-purchase case this exists to catch.
     */
    const val SMALL_FRACTION: Double = 0.5

    /**
     * The band for [fills], or `null` when there is too little history for one.
     *
     * Amounts are read straight off the fills rather than weighted by recency. Pump prices
     * move a few percent a year and tank sizes do not change, so the older fills are as
     * good a guide as the recent ones.
     */
    fun of(fills: List<FuelFill>): Band? {
        if (fills.size < MIN_FILLS) return null
        val sorted = fills.map { it.amount.paise }.sorted()
        return Band(
            low = percentile(sorted, 0.25),
            high = percentile(sorted, 0.75),
        )
    }

    /**
     * The value at [fraction] through [sorted], picked by position rather than interpolated.
     *
     * Interpolating would invent an amount nobody ever paid. The band is shown to the owner
     * as their own usual spend, so both ends should be figures they recognise.
     */
    private fun percentile(sorted: List<Long>, fraction: Double): Amount {
        val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.lastIndex)
        return Amount.of(sorted[index]).getOrNull() ?: Amount.ZERO
    }
}

/**
 * The middle half of an owner's past fill amounts.
 *
 * [low] and [high] can be equal, for an owner who always pays the same round figure. That
 * is a valid band, and [isUnusuallySmall] still works against it.
 */
data class Band(
    val low: Amount,
    val high: Amount,
) {
    /** Whether [amount] sits inside the band. */
    fun contains(amount: Amount): Boolean = amount.paise in low.paise..high.paise

    /**
     * Whether [amount] is small enough that it may not be fuel at all.
     *
     * This is the only direction that gets questioned. A payment far *above* the usual band
     * is still a fill — a full tank after months of top-ups — and asking about it would
     * turn a correct detection into a confusing one.
     */
    fun isUnusuallySmall(amount: Amount): Boolean =
        amount.paise < (low.paise * UsualFillBand.SMALL_FRACTION).toLong()
}
