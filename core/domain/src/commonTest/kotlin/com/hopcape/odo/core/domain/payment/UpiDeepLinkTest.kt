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
