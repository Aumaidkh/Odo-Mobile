package com.hopcape.odo.core.domain.shared

import arrow.core.getOrElse

/**
 * An inclusive money range, both bounds as [Amount] (paise) — e.g. a rough resale-uplift
 * estimate "Rs. 20,000–32,000". Money is *always* [Amount], never raw rupee ints, so a
 * range stays in paise like every other money value in the domain.
 */
data class AmountRange(val low: Amount, val high: Amount) {
    companion object {
        /** Build from non-negative paise bounds (always valid, since paise ≥ 0). */
        fun ofPaise(lowPaise: Long, highPaise: Long): AmountRange = AmountRange(
            low = Amount.of(lowPaise).getOrElse { Amount.ZERO },
            high = Amount.of(highPaise).getOrElse { Amount.ZERO },
        )
    }
}
