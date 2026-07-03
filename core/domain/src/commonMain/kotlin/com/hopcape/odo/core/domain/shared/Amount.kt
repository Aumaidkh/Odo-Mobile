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
