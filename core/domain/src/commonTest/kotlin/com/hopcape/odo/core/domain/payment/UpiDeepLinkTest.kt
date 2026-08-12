package com.hopcape.odo.core.domain.payment

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.payment.model.UpiPaymentRequest
import com.hopcape.odo.core.domain.payment.model.UpiPaymentStatus
import com.hopcape.odo.core.domain.payment.model.UpiVpa
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpiDeepLinkTest {

    private val request = UpiPaymentRequest(
        vpa = UpiVpa.of("pump@ybl").getOrElse { error("valid vpa") },
        payeeName = "Bharat Petroleum & Co",
    )

    @Test
    fun builds_a_link_with_the_confirmed_amount() {
        val link = UpiDeepLink.build(request, amount(200_050))
        assertTrue(link.startsWith("upi://pay?pa=pump%40ybl"))
        assertTrue(link.contains("&am=2000.50"))
        assertTrue(link.contains("&cu=INR"))
    }

    @Test
    fun a_payee_name_containing_an_ampersand_is_escaped() {
        // Unescaped, the "&" would split the link into a parameter nobody meant to send.
        val link = UpiDeepLink.build(request, amount(100))
        assertTrue(link.contains("Bharat%20Petroleum%20%26%20Co"))
    }

    @Test
    fun paise_below_a_rupee_keep_both_decimal_places() {
        assertTrue(UpiDeepLink.build(request, amount(5)).contains("&am=0.05"))
    }

    @Test
    fun the_standard_link_is_always_offered_first() {
        val candidates = UpiDeepLink.candidates(request, amount(200_050), reference = "ODO1")
        assertTrue(candidates.first().startsWith("upi://pay?"))
    }

    @Test
    fun every_candidate_carries_what_an_intent_payment_requires() {
        // Handing a payment to another app is a merchant-initiated one whoever the payee is,
        // and that route wants a reference unique to the attempt.
        UpiDeepLink.candidates(request, amount(100), reference = "ODO1755").forEach { link ->
            assertTrue(link.contains("&tr=ODO1755"), "no reference: $link")
        }
    }

    @Test
    fun a_plain_built_link_stays_exactly_what_the_code_asked_for() {
        // build() is the code's own payment, not an intent hand-off: nothing is filled in.
        val link = UpiDeepLink.build(request, amount(100))
        assertTrue(!link.contains("&tr="), "invented a reference: $link")
        assertTrue(!link.contains("&mc="), "invented a category: $link")
    }

    @Test
    fun every_candidate_asks_for_the_same_money_from_the_same_payee() {
        // A fallback that carried a different amount would be the worst kind of bug: money
        // moves, and the figure the owner confirmed is not the one that left the account.
        val candidates = UpiDeepLink.candidates(request, amount(200_050), reference = "ODO1")
        candidates.forEach { link ->
            assertTrue(link.contains("pa=pump%40ybl"), "wrong payee: $link")
            assertTrue(link.contains("&am=2000.50"), "wrong amount: $link")
            assertTrue(link.contains("&cu=INR"), "wrong currency: $link")
        }
    }

    @Test
    fun the_fallbacks_are_the_app_schemes_and_nothing_else() {
        val candidates = UpiDeepLink.candidates(request, amount(100), reference = "ODO1")
        assertEquals(
            listOf("upi://pay", "tez://upi/pay", "phonepe://pay", "paytmmp://pay"),
            candidates.map { it.substringBefore('?') },
        )
    }

    @Test
    fun a_real_merchants_reference_and_category_are_never_overwritten() {
        // A pump reconciles against its own, so the stand-ins must never displace them.
        val pump = request.copy(transactionRef = "PUMP-42", merchantCode = "5541")
        UpiDeepLink.candidates(pump, amount(100), reference = "ODO1755").forEach { link ->
            assertTrue(link.contains("&tr=PUMP-42"), "lost the code's reference: $link")
            assertTrue(link.contains("&mc=5541"), "lost the code's category: $link")
            assertTrue(!link.contains("ODO1755"), "generated reference leaked in: $link")
        }
    }

    @Test
    fun what_a_merchant_code_is_validated_against_survives() {
        // Drop the signature and the payment authorised is not the payment being made.
        val scanned = "upi://pay?pa=pump@ybl&pn=Pump&mode=02&purpose=00&orgid=159761&sign=AbC%2B12"
        val parsed = UpiQrParser.parse(scanned).getOrElse { error("expected a parsed request, got $it") }
        assertEquals(
            mapOf("mode" to "02", "purpose" to "00", "orgid" to "159761", "sign" to "AbC+12"),
            parsed.extras,
        )
        UpiDeepLink.candidates(parsed, amount(100), reference = "ODO1").forEach { link ->
            assertTrue(link.contains("sign=AbC%2B12"), "lost the signature: $link")
            assertTrue(link.contains("orgid=159761"), "lost the originator: $link")
        }
    }

    @Test
    fun another_apps_own_token_is_never_handed_back_to_it() {
        // The real Google Pay code from the bug: `aid` is Google Pay's, not the spec's, and
        // replaying it from a different app is what the authenticity checks are looking for.
        // Google Pay reports that as the bank refusing a one-rupee payment for exceeding a limit.
        val scanned = "upi://pay?pa=aumaidm.m.c@okaxis&pn=Murtaza%20Khursheed&aid=uGICAgIC1tJnzJw"
        val parsed = UpiQrParser.parse(scanned).getOrElse { error("expected a parsed request, got $it") }
        assertEquals(emptyMap(), parsed.extras)

        // Matched as a parameter, not as text: the payee's own address contains "aid".
        UpiDeepLink.candidates(parsed, amount(100), reference = "ODO1").forEach { link ->
            assertTrue(!link.contains("&aid="), "replayed another app's token: $link")
            assertTrue(link.contains("pa=aumaidm.m.c%40okaxis"), "lost the payee: $link")
        }
    }

    @Test
    fun a_modelled_field_is_never_also_repeated_as_an_extra() {
        val scanned = "upi://pay?pa=pump@ybl&pn=Pump&am=10.00&cu=INR&tn=Fuel&mc=5541&tr=REF1"
        val parsed = UpiQrParser.parse(scanned).getOrElse { error("expected a parsed request, got $it") }
        assertEquals(emptyMap(), parsed.extras)

        // The currency is rebuilt, never echoed, so it appears exactly once.
        val link = UpiDeepLink.build(parsed, amount(100))
        assertEquals(1, link.split("cu=").size - 1, "currency repeated: $link")
    }

    @Test
    fun no_category_is_claimed_for_a_payment_that_has_none() {
        // Odo is not the merchant. Sending a stand-in `mc` was tried against the real failure
        // and changed nothing, so what is left is an unsupportable claim about the payment.
        UpiDeepLink.candidates(request, amount(100), reference = "ODO1").forEach { link ->
            assertTrue(!link.contains("&mc="), "claimed a merchant category: $link")
        }
    }

    @Test
    fun a_success_response_is_read_with_its_references() {
        val result = UpiDeepLink.parseResponse(
            "txnId=AXI123&responseCode=00&Status=SUCCESS&txnRef=REF9",
        )
        assertEquals(UpiPaymentStatus.Success, result.status)
        assertEquals("AXI123", result.transactionId)
        assertEquals("REF9", result.transactionRef)
        assertTrue(result.isConfirmed)
    }

    @Test
    fun submitted_is_pending_and_never_confirmed() {
        // Pending must not produce a fuel fill: the money may or may not have moved.
        val result = UpiDeepLink.parseResponse("txnId=A&Status=SUBMITTED")
        assertEquals(UpiPaymentStatus.Pending, result.status)
        assertTrue(!result.isConfirmed)
    }

    @Test
    fun anything_unreadable_is_a_failure_rather_than_a_success() {
        listOf(null, "", "garbage", "Status=WHATEVER").forEach { response ->
            val result = UpiDeepLink.parseResponse(response)
            assertEquals(UpiPaymentStatus.Failed, result.status, "response=$response")
            assertTrue(!result.isConfirmed)
        }
    }

    @Test
    fun a_built_link_parses_back_to_the_same_request() {
        val link = UpiDeepLink.build(request, amount(200_050))
        val parsed = UpiQrParser.parse(link).getOrElse { error("expected a parsed request, got $it") }
        assertEquals(request.vpa.value, parsed.vpa.value)
        assertEquals(request.payeeName, parsed.payeeName)
        assertEquals(200_050, parsed.amount?.paise)
    }

    private fun amount(paise: Long): Amount = Amount.of(paise).getOrElse { error("valid amount") }
}
