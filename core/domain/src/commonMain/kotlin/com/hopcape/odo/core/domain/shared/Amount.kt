package com.hopcape.odo.core.domain.shared

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlin.jvm.JvmInline

/**
 * A monetary amount in **integer paise** — guaranteed `>= 0`.
 *
 * Money is always stored and computed as integer paise across the app (CLAUDE.md /
 * DB_SCHEMA): ₹2,800 → `280000`. Rupees appear only in the UI layer; never a
 * `Double`/`Float` in a money path, so fairness math stays exact.
 *
 * Shared-kernel value object living alongside [Distance]. Construct only via [of]; a
 * negative amount can never exist. A missing amount defaults to [ZERO] — a service
 * with no recorded cost is `0` paise, not an error.
 */
@JvmInline
value class Amount private constructor(val paise: Long) {

    /** Sum of two amounts — both are non-negative, so the result always is too. */
    operator fun plus(other: Amount): Amount = Amount(paise + other.paise)

    /**
     * This amount repeated [factor] times — a paise-per-km rate times the kilometres
     * driven, for instance. [factor] must not be negative; a negative count is a
     * programming error, not a value a caller can recover from.
     */
    operator fun times(factor: Int): Amount {
        require(factor >= 0) { "cannot scale an amount by $factor" }
        return Amount(paise * factor)
    }

    /**
     * This amount spread over [distance], rounded to the nearest paise — the per-km rate
     * the cost tracker is built on.
     *
     * `null` for zero distance: a cost with no kilometres behind it has no rate, and
     * returning zero there would read as "this car is free to run".
     */
    fun perKm(distance: Distance): Amount? {
        if (distance.km == 0) return null
        return Amount((paise + distance.km / 2) / distance.km)
    }

    companion object {
        /** No recorded cost — the default for a manually-logged service. */
        val ZERO = Amount(0)

        fun of(paise: Long?): Either<DomainError, Amount> = when {
            paise == null -> ZERO.right()
            paise < 0 -> DomainError.NegativeAmount.left()
            else -> Amount(paise).right()
        }
    }
}

/** Total of a sequence of amounts (empty → [Amount.ZERO]) — keeps money math in [Amount]. */
fun Iterable<Amount>.sum(): Amount = fold(Amount.ZERO) { acc, amount -> acc + amount }
