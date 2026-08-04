package com.hopcape.odo.core.domain.payment

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpiQrParserTest {

    @Test
    fun parses_a_full_payment_link() {
        val request = UpiQrParser.parse(
            "upi://pay?pa=bharatpetroleum@ybl&pn=Bharat%20Petroleum&am=2000.50&cu=INR&tn=Fuel&mc=5541&tr=REF9",
        ).getOrElse { error("expected a parsed request, got $it") }

        assertEquals("bharatpetroleum@ybl", request.vpa.value)
        assertEquals("Bharat Petroleum", request.payeeName)
        assertEquals(200_050, request.amount?.paise)
        assertEquals("Fuel", request.transactionNote)
        assertEquals("5541", request.merchantCode)
        assertEquals("REF9", request.transactionRef)
    }

    @Test
    fun a_code_with_no_amount_is_valid_and_leaves_the_sum_open() {
        val request = UpiQrParser.parse("upi://pay?pa=pump@okaxis&pn=Pump")
            .getOrElse { error("expected a parsed request, got $it") }

        assertNull(request.amount)
        assertTrue(!request.hasAmount)
    }

    @Test
    fun rupees_become_exact_paise() {
        // 104.50 is not representable in binary floating point. Parsed as text, it must land
        // on 10450 exactly — this is the whole reason money is never a Double here.
        val request = UpiQrParser.parse("upi://pay?pa=a@b&am=104.50")
            .getOrElse { error("expected a parsed request") }
        assertEquals(10_450, request.amount?.paise)
    }

    @Test
    fun a_single_decimal_place_is_padded_not_truncated() {
        val request = UpiQrParser.parse("upi://pay?pa=a@b&am=104.5")
            .getOrElse { error("expected a parsed request") }
        assertEquals(10_450, request.amount?.paise)
    }

    @Test
    fun whole_rupees_parse() {
        val request = UpiQrParser.parse("upi://pay?pa=a@b&am=2000")
            .getOrElse { error("expected a parsed request") }
        assertEquals(200_000, request.amount?.paise)
    }

    @Test
    fun a_non_upi_payload_is_rejected_rather_than_guessed_at() {
        // An EMVCo/Bharat QR encodes the same payment in a different grammar. Misreading one
        // would send money somewhere else, so it is refused.
        assertEquals(
            DomainError.UnsupportedQr,
            UpiQrParser.parse("00020101021226580011").leftOrNull(),
        )
        assertEquals(DomainError.UnsupportedQr, UpiQrParser.parse("https://example.com").leftOrNull())
        assertEquals(DomainError.UnsupportedQr, UpiQrParser.parse(null).leftOrNull())
    }

    @Test
    fun a_malformed_address_is_rejected() {
        assertEquals(DomainError.InvalidUpiAddress, UpiQrParser.parse("upi://pay?pa=nohandle").leftOrNull())
        assertEquals(DomainError.InvalidUpiAddress, UpiQrParser.parse("upi://pay?pn=NoAddress").leftOrNull())
        assertEquals(DomainError.InvalidUpiAddress, UpiQrParser.parse("upi://pay?pa=@bank").leftOrNull())
    }

    @Test
    fun a_malformed_amount_is_rejected_rather_than_ignored() {
        // Dropping an unreadable amount would silently ask the owner to type one, which is
        // the same screen they would see for a code that never carried one.
        assertEquals(DomainError.InvalidUpiAmount, UpiQrParser.parse("upi://pay?pa=a@b&am=12.345").leftOrNull())
        assertEquals(DomainError.InvalidUpiAmount, UpiQrParser.parse("upi://pay?pa=a@b&am=abc").leftOrNull())
    }

    @Test
    fun the_first_of_two_duplicate_parameters_wins() {
        val request = UpiQrParser.parse("upi://pay?pa=first@bank&pa=second@bank")
            .getOrElse { error("expected a parsed request") }
        assertEquals("first@bank", request.vpa.value)
    }

    @Test
    fun the_scheme_is_matched_case_insensitively() {
        assertTrue(UpiQrParser.parse("UPI://pay?pa=a@b").isRight())
    }
}

private fun <A, B> arrow.core.Either<A, B>.leftOrNull(): A? = fold({ it }, { null })
