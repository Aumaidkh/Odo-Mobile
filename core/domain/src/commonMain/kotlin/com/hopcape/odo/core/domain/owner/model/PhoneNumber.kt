package com.hopcape.odo.core.domain.owner.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * The number someone signs in with, in E.164.
 *
 * E.164 because that is the only form the auth provider accepts and the only one that is
 * unambiguous: `9812345678` is a valid subscriber number in several countries, and the SMS
 * has to reach exactly one of them.
 *
 * **India is assumed, not required.** Odo's market is Indian car owners (PRD), so a bare
 * ten-digit number gets [DEFAULT_COUNTRY_CODE] and the keypad can stay simple. Someone who
 * types a `+` is taken at their word — the default is a convenience, not a restriction.
 *
 * Input is normalised before it is judged: spaces, hyphens and brackets are how people
 * actually write phone numbers, and rejecting `98123 45678` would be rejecting a number
 * that is perfectly correct.
 *
 * Construct only via [of].
 */
@JvmInline
value class PhoneNumber private constructor(val value: String) {

    /**
     * The last four digits, for "sent to a number ending 4321" copy.
     *
     * Four, not the whole number: the screen is confirming which number the owner typed, and
     * a full number on screen is a full number in a screenshot.
     */
    val lastFourDigits: String get() = value.takeLast(4)

    companion object {
        /** India. Applied when the owner types a bare national number. */
        const val DEFAULT_COUNTRY_CODE = "+91"

        /** An Indian mobile number is ten digits. */
        const val NATIONAL_LENGTH = 10

        /** E.164 allows at most fifteen digits after the `+`. */
        const val MAX_DIGITS = 15

        /** Shortest number E.164 considers plausible; below this it is a typo, not a number. */
        const val MIN_DIGITS = 8

        fun of(raw: String?): Either<DomainError, PhoneNumber> {
            val cleaned = raw.orEmpty().filterNot { it.isWhitespace() || it in SEPARATORS }
            if (cleaned.isEmpty()) return DomainError.BlankPhoneNumber.left()

            val explicitCountryCode = cleaned.startsWith('+')
            val digits = cleaned.removePrefix("+")
            // A `+` followed by anything non-numeric is not a phone number at all.
            if (digits.isEmpty() || !digits.all { it.isDigit() }) {
                return DomainError.InvalidPhoneNumber.left()
            }

            val e164 = when {
                explicitCountryCode -> "+$digits"
                // A bare national number is the common case on an Indian keypad.
                digits.length == NATIONAL_LENGTH -> "$DEFAULT_COUNTRY_CODE$digits"
                // Anything else without a country code is ambiguous, and guessing which
                // country an SMS should go to is not a guess worth making.
                else -> return DomainError.InvalidPhoneNumber.left()
            }

            val count = e164.length - 1
            return if (count in MIN_DIGITS..MAX_DIGITS) {
                PhoneNumber(e164).right()
            } else {
                DomainError.InvalidPhoneNumber.left()
            }
        }

        private val SEPARATORS = setOf('-', '(', ')', '.')
    }
}
