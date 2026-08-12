package com.hopcape.odo.core.domain.owner.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * What Odo calls the owner — a trimmed name between [MIN_LENGTH] and [MAX_LENGTH]
 * characters.
 *
 * Unlike [com.hopcape.odo.core.domain.shared.WorkshopName], absence is an *error* rather
 * than a `null` result: this type only exists once someone has actually given a name, and
 * every screen that greets them ("Namaste, Rahul") depends on that.
 *
 * [MIN_LENGTH] is 2 because a single character is almost always a typo or a stray
 * keystroke, and a greeting built from it reads as broken. Indian single-word names
 * ("Ravi") are common, so no space is required — only length.
 *
 * Construct only via [of].
 */
@JvmInline
value class OwnerName private constructor(val value: String) {
    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 60

        fun of(raw: String?): Either<DomainError, OwnerName> {
            val trimmed = raw?.trim()
            return when {
                trimmed.isNullOrEmpty() -> DomainError.BlankOwnerName.left()
                trimmed.length < MIN_LENGTH -> DomainError.OwnerNameTooShort(MIN_LENGTH).left()
                trimmed.length > MAX_LENGTH -> DomainError.OwnerNameTooLong(MAX_LENGTH).left()
                else -> OwnerName(trimmed).right()
            }
        }
    }
}
