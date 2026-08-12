package com.hopcape.odo.core.domain.fairness.model

/**
 * How much to trust a [FairnessEstimate], derived from its sample size.
 *
 * PRD guardrail: fairness data must never show false precision — with **fewer than 5
 * data points** the verdict is [LOW] and the UI must show a range with an explicit
 * low-confidence label, not a precise "over/under".
 */
enum class FairnessConfidence {
    LOW, MEDIUM, HIGH;

    companion object {
        fun of(sampleSize: Int): FairnessConfidence = when {
            sampleSize < 5 -> LOW
            sampleSize < 20 -> MEDIUM
            else -> HIGH
        }
    }
}
