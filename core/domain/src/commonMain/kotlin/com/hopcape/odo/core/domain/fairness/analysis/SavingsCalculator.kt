package com.hopcape.odo.core.domain.fairness.analysis

import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.shared.sum

/**
 * Adds up every overcharge fairness has caught on a car.
 *
 * A pure domain service — no repository, no clock. It reads the verdicts already stored on
 * the entries ([ServiceLogEntry.fairness]), so counting them never re-queries the benchmark
 * pool and the figure cannot drift as other owners contribute bills.
 *
 * It lives in `:core:domain` because two surfaces show the same number: the service log's
 * header and Home's stat card. A feature may not import another feature, so the arithmetic
 * sits in the shared kernel — the same reason
 * [RunningCostCalculator][com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator]
 * does. Two independent sums would eventually disagree, and the owner would be looking at
 * two totals for one fact.
 */
object SavingsCalculator {

    /**
     * What [entries] were overcharged by in total, and how many of them were.
     *
     * Lifetime, not windowed: the figure answers "how much has Odo caught for me", which is
     * a running total of the whole record. Entries nobody checked, and entries judged fair,
     * contribute nothing rather than zero-valued rows.
     */
    fun of(entries: List<ServiceLogEntry>): FairnessSavings {
        val overcharges = entries.mapNotNull { it.fairness?.overchargedBy }
        if (overcharges.isEmpty()) return FairnessSavings.NONE
        return FairnessSavings(
            overchargeTotal = overcharges.sum(),
            overchargesCaught = overcharges.size,
        )
    }
}
