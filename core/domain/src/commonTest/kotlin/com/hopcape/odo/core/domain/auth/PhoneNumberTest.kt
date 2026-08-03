package com.hopcape.odo.core.domain.auth

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Turning what someone types into something an SMS can reach.
 *
 * The cases that matter are the ones a real keypad produces: a bare ten-digit number, a
 * number written with spaces, and a number with the country code already on it.
 */
class PhoneNumberTest {

    @Test
    fun aBareTenDigitNumberGetsIndiasCountryCode() {
        // The common case on an Indian keypad, and the reason the default exists.
        assertEquals("+919812345678", parsed("9812345678"))
    }

    @Test
    fun theWayPeopleActuallyWriteNumbersIsAccepted() {
        // Rejecting these would be rejecting numbers that are perfectly correct.
        assertEquals("+919812345678", parsed("98123 45678"))
        assertEquals("+919812345678", parsed("98123-45678"))
        assertEquals("+919812345678", parsed("(98123) 45678"))
        assertEquals("+919812345678", parsed("+91 98123 45678"))
    }

    @Test
    fun anExplicitCountryCodeIsTakenAtItsWord() {
        // The default is a convenience for Indian numbers, not a restriction to them.
        assertEquals("+14155550123", parsed("+1 415 555 0123"))
        assertEquals("+442071838750", parsed("+44 20 7183 8750"))
    }

    @Test
    fun anAmbiguousNationalNumberIsRefused() {
        // Nine digits with no country code could belong to several countries, and guessing
        // where to send an SMS is not a guess worth making.
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("981234567"))
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("981234567890"))
    }

    @Test
    fun nothingTypedIsItsOwnError() {
        // Distinct from invalid: the field is empty, not wrong, so the screen says
        // "enter your number" rather than "that number is not valid".
        assertIs<DomainError.BlankPhoneNumber>(failureFor(""))
        assertIs<DomainError.BlankPhoneNumber>(failureFor("   "))
        assertIs<DomainError.BlankPhoneNumber>(failureFor(null))
    }

    @Test
    fun lettersAreNotAPhoneNumber() {
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("98123abcde"))
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("+91 98123 4567x"))
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("+"))
    }

    @Test
    fun e164LengthLimitsAreEnforced() {
        // Above fifteen digits is not a number E.164 can carry.
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("+1234567890123456"))
        // Below eight is a typo, not a number.
        assertIs<DomainError.InvalidPhoneNumber>(failureFor("+1234567"))
    }

    @Test
    fun onlyTheLastFourDigitsAreExposedForCopy() {
        val phone = PhoneNumber.of("9812345678").getOrElse { error("expected a valid number") }

        // A full number on screen is a full number in a screenshot.
        assertEquals("5678", phone.lastFourDigits)
    }

    private fun parsed(raw: String): String =
        PhoneNumber.of(raw).getOrElse { error("expected $raw to parse, got $it") }.value

    private fun failureFor(raw: String?): DomainError =
        PhoneNumber.of(raw).fold(ifLeft = { it }, ifRight = { error("expected $raw to fail, got $it") })
}
