package com.hopcape.odo.feature.healthscore.domain.model

import com.hopcape.odo.core.domain.health.model.HealthScore

/**
 * Everything the health-score screen needs in one emission: the score as it stands, how it
 * has moved, and whether the owner may see the breakdown.
 *
 * The three travel together so the screen renders one consistent picture. Reading the
 * entitlement separately would let the dial and the paywall arrive on different frames.
 */
internal data class HealthScoreSummary(
    val score: HealthScore,

    /**
     * Points gained (or lost) against the score from a month ago. `null` when there is no
     * snapshot that old — the screen hides the badge rather than comparing against last
     * week's number and calling it a month.
     */
    val delta: Int?,

    /** Pro sees every factor; free sees the first one and a paywall. */
    val isPro: Boolean,
)
