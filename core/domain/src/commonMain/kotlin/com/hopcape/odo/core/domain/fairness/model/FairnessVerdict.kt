package com.hopcape.odo.core.domain.fairness.model

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Amount

/**
 * How a paid amount compares to the city average for its category.
 *
 * [LowConfidence] is returned when the estimate has too few data points to judge
 * (guardrail: no false precision) — the UI shows the range + sample size instead of a
 * verdict. A ±[TOLERANCE] band around the average counts as [Fair].
 */
sealed interface FairnessVerdict {
    data object Fair : FairnessVerdict
    data class Over(val by: Amount) : FairnessVerdict
    data class Under(val by: Amount) : FairnessVerdict
    data class LowConfidence(val estimate: FairnessEstimate) : FairnessVerdict

    companion object {
        private const val TOLERANCE = 0.10

        fun of(actual: Amount, estimate: FairnessEstimate): FairnessVerdict {
            if (estimate.confidence == FairnessConfidence.LOW) return LowConfidence(estimate)
            val avg = estimate.cityAverage.paise
            val band = (avg * TOLERANCE).toLong()
            return when {
                actual.paise > avg + band -> Over(amount(actual.paise - avg))
                actual.paise < avg - band -> Under(amount(avg - actual.paise))
                else -> Fair
            }
        }

        private fun amount(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
    }
}
