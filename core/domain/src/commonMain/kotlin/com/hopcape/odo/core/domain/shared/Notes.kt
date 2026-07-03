package com.hopcape.odo.core.domain.shared

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlin.jvm.JvmInline

/**
 * Free-text notes on a service log — a trimmed string capped at [MAX_LENGTH].
 *
 * Optional (DB `notes` is nullable), so [of] maps null/blank input to a `null` result
 * (absent) rather than an error; only over-long text fails validation. Construct only
 * via [of].
 */
@JvmInline
value class Notes private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 1_000

        fun of(raw: String?): Either<DomainError, Notes?> {
            val trimmed = raw?.trim()
            return when {
                trimmed.isNullOrEmpty() -> null.right()
                trimmed.length > MAX_LENGTH -> DomainError.NotesTooLong(MAX_LENGTH).left()
                else -> Notes(trimmed).right()
            }
        }
    }
}
