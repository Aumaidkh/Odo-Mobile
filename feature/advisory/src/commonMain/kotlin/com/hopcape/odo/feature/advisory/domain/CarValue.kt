package com.hopcape.odo.feature.advisory.domain

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.AmountRange

/**
 * What a car is worth, and what proving its history would add.
 *
 * Two figures rather than one, because the second is the point: the gap between them is the
 * rupee answer to "why should I scan a bill?", and it is the only number on the screen the
 * owner can change.
 *
 * @param today what the car fetches with the record it has right now.
 * @param withFullRecord the band it would fetch with a complete, documented history.
 * @param recordWorth the middle of that band less [today] — the gap, as one number.
 * @param recordCompleteness 0 to 1, how much of the expected history is proven by a bill.
 * @param provenServices how many services have a bill behind them.
 */
data class CarValue(
    val today: Amount,
    val withFullRecord: AmountRange,
    val recordWorth: Amount,
    val recordCompleteness: Double,
    val provenServices: Int,
) {
    /**
     * The owner has proved nothing yet — the day-1 case, and the one worth a nudge.
     *
     * Counted, not read off [recordCompleteness]. A car in its first year is complete with
     * no bills at all, because it has nothing to prove; saying "with your record" to that
     * owner would credit them with a record they have never had.
     */
    val hasNoRecord: Boolean get() = provenServices == 0
}
