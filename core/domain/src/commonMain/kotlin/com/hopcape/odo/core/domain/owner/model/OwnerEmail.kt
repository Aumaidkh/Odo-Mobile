package com.hopcape.odo.core.domain.owner.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * An optional contact email for the owner.
 *
 * Optional the way [com.hopcape.odo.core.domain.shared.WorkshopName] is: null or blank
 * input maps to a `null` result rather than an error, because Odo signs people in by phone
 * and never requires an address. Only a *present but malformed* one fails.
 *
 * The check is deliberately loose — something before an `@`, a dot-separated domain after
 * it, no spaces. A stricter pattern rejects addresses that work, and the only real
 * verification is sending mail to it, which Odo does not do yet.
 *
 * Construct only via [of].
 */
@JvmInline
value class OwnerEmail private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 254

        fun of(raw: String?): Either<DomainError, OwnerEmail?> {
            val trimmed = raw?.trim()
            return when {
                trimmed.isNullOrEmpty() -> null.right()
                trimmed.length > MAX_LENGTH -> DomainError.OwnerEmailTooLong(MAX_LENGTH).left()
                !trimmed.looksLikeAnAddress() -> DomainError.InvalidOwnerEmail.left()
                else -> OwnerEmail(trimmed).right()
            }
        }

        private fun String.looksLikeAnAddress(): Boolean {
            if (any { it.isWhitespace() }) return false
            val at = indexOf('@')
            if (at <= 0 || at != lastIndexOf('@')) return false
            val domain = substring(at + 1)
            val dot = domain.lastIndexOf('.')
            return dot > 0 && dot < domain.length - 1
        }
    }
}
