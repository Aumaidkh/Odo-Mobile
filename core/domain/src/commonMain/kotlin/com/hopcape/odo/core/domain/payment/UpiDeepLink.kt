package com.hopcape.odo.core.domain.payment

import com.hopcape.odo.core.domain.payment.model.UpiPaymentRequest
import com.hopcape.odo.core.domain.payment.model.UpiPaymentResult
import com.hopcape.odo.core.domain.payment.model.UpiPaymentStatus
import com.hopcape.odo.core.domain.shared.Amount

/**
 * Builds the `upi://pay?...` link a UPI app is handed, and reads back what it answers.
 *
 * Both directions live in the domain rather than in the platform layer, so the platform's
 * only job is "open this string, give me back that string". That keeps the Android and iOS
 * sides free of any knowledge about UPI, and puts the part that can be wrong — the grammar —
 * where it can be tested.
 */
object UpiDeepLink {

    /**
     * The link for [request], with [amount] as the sum to collect.
     *
     * The amount is a parameter rather than being read off the request because a fuel-pump
     * code usually carries none and the owner types it. Passing it explicitly means the
     * confirmed figure is the one that goes to the bank, with no chance of the original
     * (empty) value leaking through.
     */
    fun build(request: UpiPaymentRequest, amount: Amount): String = buildString {
        append("upi://pay?pa=").append(encode(request.vpa.value))
        request.payeeName?.let { append("&pn=").append(encode(it)) }
        append("&am=").append(rupees(amount))
        // Rupees, always and explicitly. UPI accepts no other currency, and an app that
        // defaults differently would silently be asking for something else.
        append("&cu=INR")
        request.transactionNote?.let { append("&tn=").append(encode(it)) }
        request.merchantCode?.let { append("&mc=").append(encode(it)) }
        request.transactionRef?.let { append("&tr=").append(encode(it)) }
    }

    /**
     * Read the `response` string a UPI app returns —
     * `txnId=…&responseCode=00&Status=SUCCESS&txnRef=…`.
     *
     * Anything that is not plainly a success is **not** treated as one. An empty or
     * unparseable response means the owner backed out, the app crashed, or something was
     * returned that this build does not understand, and none of those are grounds for
     * writing a fuel fill.
     */
    fun parseResponse(response: String?): UpiPaymentResult {
        val params = (response ?: "")
            .split('&')
            .filter { it.isNotBlank() }
            .associate { it.substringBefore('=').lowercase() to it.substringAfter('=', "") }

        val status = when (params["status"]?.uppercase()) {
            "SUCCESS" -> UpiPaymentStatus.Success
            "SUBMITTED", "PENDING" -> UpiPaymentStatus.Pending
            else -> UpiPaymentStatus.Failed
        }
        return UpiPaymentResult(
            status = status,
            transactionId = params["txnid"]?.takeIf { it.isNotBlank() },
            transactionRef = params["txnref"]?.takeIf { it.isNotBlank() },
        )
    }

    /** Paise back to the `123.45` the link expects. Integer arithmetic, never a `Double`. */
    private fun rupees(amount: Amount): String {
        val whole = amount.paise / 100
        val fraction = (amount.paise % 100).toString().padStart(2, '0')
        return "$whole.$fraction"
    }

    /**
     * Percent-encode a query value.
     *
     * Everything outside the unreserved set is escaped, which is stricter than necessary and
     * deliberately so: a merchant name with an `&` in it would otherwise split the link into
     * a parameter nobody meant to send.
     */
    private fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() && byte.toInt() in 0..127 || char in UNRESERVED) {
                append(char)
            } else {
                append('%').append(byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    private const val UNRESERVED = "-_.~"
}
