package com.hopcape.odo.core.domain.health.model

/**
 * A car's health score — one number out of 100, and the four factors it is made of.
 *
 * Built by
 * [HealthScoreCalculator][com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator],
 * which is the app's only scoring math. Everything here is derived from [factors], so a
 * score can never disagree with the breakdown it is shown next to.
 *
 * **Points are earned, never granted.** A factor Odo has no evidence for scores zero, so a
 * car added today starts low and climbs as its owner logs services, uploads papers and
 * scans bills. The alternative — full marks until proven otherwise — would mean the score
 * *falls* the more honestly the app is used, and would be the false precision the PRD
 * forbids.
 */
data class HealthScore(
    val factors: List<HealthFactor>,
) {
    /** The 0–100 headline. */
    val total: Int get() = factors.sumOf { it.earned }

    val band: HealthBand get() = HealthBand.of(total)

    /**
     * The factor with the most points still to earn — the "biggest opportunity" nudge.
     * `null` only at a perfect 100, where there is nothing to suggest.
     *
     * Ties break towards the heavier factor, because that is where the owner's next
     * action pays best.
     */
    val biggestGap: HealthFactor?
        get() = factors
            .filter { it.missing > 0 }
            .maxWithOrNull(compareBy({ it.missing }, { it.kind.weight }))

    /** This factor's line in the breakdown, for callers that want one by name. */
    fun factorFor(kind: HealthFactorKind): HealthFactor? = factors.firstOrNull { it.kind == kind }

    /**
     * How the score moved against an earlier one — the "+6 points this month" line.
     *
     * Positive means it climbed. `null` when there is no earlier score to compare with,
     * which is the screen's cue to hide the badge rather than show a reassuring zero.
     */
    fun deltaFrom(previous: HealthScore?): Int? = previous?.let { total - it.total }
}
