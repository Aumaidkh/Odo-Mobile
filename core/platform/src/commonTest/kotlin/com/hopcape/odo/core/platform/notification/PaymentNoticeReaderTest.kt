package com.hopcape.odo.core.platform.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentNoticeReaderTest {

    @Test
    fun readsTheMerchantAndAmountOutOfAUpiPaymentLine() {
        val text = "You paid Rs. 2,000 to Bharat Petroleum, Karol Bagh"

        assertEquals("Bharat Petroleum, Karol Bagh", PaymentNoticeReader.merchantIn(text))
        assertEquals(200_000L, PaymentNoticeReader.amountIn(text))
    }

    @Test
    fun readsTheRupeeSymbolAsWellAsTheAbbreviation() {
        assertEquals(200_000L, PaymentNoticeReader.amountIn("Paid ₹2000 to HP Petrol Pump"))
        assertEquals(200_000L, PaymentNoticeReader.amountIn("INR 2000.00 debited"))
    }

    @Test
    fun readsOtherMarketsCurrenciesToo() {
        assertEquals(4_520L, PaymentNoticeReader.amountIn("$45.20 sent to Shell"))
        assertEquals(3_099L, PaymentNoticeReader.amountIn("€30.99 paid at Total"))
    }

    @Test
    fun theBranchStaysPartOfTheMerchantName() {
        // Two branches of one brand are two merchants: rejecting the shop at Karol Bagh must
        // not silence the pump in Andheri.
        assertEquals(
            "HP Retail, Karol Bagh",
            PaymentNoticeReader.merchantIn("You paid Rs. 300 to HP Retail, Karol Bagh"),
        )
    }

    @Test
    fun trailingPunctuationIsNotPartOfTheName() {
        assertEquals(
            "Shell Lamar Blvd",
            PaymentNoticeReader.merchantIn("Paid $40 to Shell Lamar Blvd."),
        )
    }

    @Test
    fun aNotificationWithNoRecipientHasNoMerchant() {
        assertNull(PaymentNoticeReader.merchantIn("Your battery is low"))
        assertNull(PaymentNoticeReader.merchantIn("2 new messages"))
    }

    @Test
    fun aBareNumberIsNotReadAsAnAmount() {
        // A card's last four digits or a reference number sit in payment text all the time.
        assertNull(PaymentNoticeReader.amountIn("Payment to Shell ref 402214 completed"))
    }

    @Test
    fun aPaymentWithNoAmountStillYieldsAMerchant() {
        val text = "Payment successful to Indian Oil Andheri"

        assertEquals("Indian Oil Andheri", PaymentNoticeReader.merchantIn(text))
        assertNull(PaymentNoticeReader.amountIn(text))
    }

    @Test
    fun paiseAreKeptWhenTheAppPrintsThem() {
        assertEquals(200_050L, PaymentNoticeReader.amountIn("You paid Rs. 2000.50 to Shell"))
    }
}
