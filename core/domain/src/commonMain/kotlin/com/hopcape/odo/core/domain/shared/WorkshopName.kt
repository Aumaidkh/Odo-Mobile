package com.hopcape.odo.core.domain.shared

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlin.jvm.JvmInline

/**
 * The workshop/garage a service was done at — a trimmed, non-blank string capped at
 * [MAX_LENGTH].
 *
 * Optional on a service log (DB `workshop_name` is nullable), so [of] maps null/blank
 * input to a `null` result (absent) rather than an error; only an over-long name
 * fails validation. Construct only via [of].
 */
@JvmInline
value class WorkshopName private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 120

        fun of(raw: String?): Either<DomainError, WorkshopName?> {
            val trimmed = raw?.trim()
            return when {
                trimmed.isNullOrEmpty() -> null.right()
                trimmed.length > MAX_LENGTH -> DomainError.WorkshopNameTooLong(MAX_LENGTH).left()
                else -> WorkshopName(trimmed).right()
            }
        }
    }
}
