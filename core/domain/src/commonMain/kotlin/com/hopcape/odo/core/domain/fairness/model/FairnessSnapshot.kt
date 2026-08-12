package com.hopcape.odo.core.domain.fairness.model

import kotlin.time.Instant

/**
 * A fairness verdict **frozen at the moment it was taken**, stored on the entry it judged.
 *
 * The city pool changes daily as other owners contribute bills. Recomputing a verdict on
 * every read would mean "you overpaid Rs. 700" quietly becoming Rs. 550 next week and the
 * ledger's "Saved so far" total drifting under the owner — for a figure the product asks
 * them to trust and act on. So the check runs once, when there is finally something to
 * check (a bill lands on the entry), and the answer is kept with its evidence: the same
 * reasoning behind `bill_line_items.fairness_snapshot` in DB_SCHEMA §5.5.
 *
 * [checkedAt] is what makes the number honest later — it dates the comparison, so a stale
 * verdict can be shown as of its date, or deliberately re-taken, rather than silently
 * passing for current.
 */
data class FairnessSnapshot(
    val report: FairnessReport,
    val checkedAt: Instant,
) {
    /** The headline as judged at [checkedAt]. */
    val outcome: FairnessOutcome get() = report.outcome

    /** How much this entry was overcharged by, or `null` if it was not. */
    val overchargedBy get() = (outcome as? FairnessOutcome.Over)?.by
}
