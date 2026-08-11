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
    fun build(request: UpiPaymentRequest, amount: Amount): String = "$STANDARD?${query(request, amount)}"

    /**
     * Every link worth trying for this payment, best first.
     *
     * The first is always the standard `upi://pay` one, which is what NPCI specifies and what
     * a correctly set-up phone answers. The rest are the payment apps' own schemes, carrying
     * the identical query.
     *
     * They exist because "installed" and "will accept a payment" are not the same thing. A UPI
     * app keeps its `upi:` activity **disabled** until UPI is set up inside it, and re-enables
     * it afterwards. On a phone where that has not happened the standard link resolves to
     * nothing at all — not hidden by package visibility, genuinely absent — while the app's own
     * scheme still answers. Odo would otherwise tell an owner looking at their Google Pay icon
     * that they have no UPI app.
     *
     * Order is not a preference: the caller is expected to offer everything that answers and
     * let the owner choose, exactly as the chooser does for the standard link.
     *
     * Only schemes that have been seen to work are listed. An invented one is worse than a
     * missing one, because it reads as coverage while resolving to nothing forever.
     *
     * **[reference] is why this is not just [build] repeated.** A payment handed to another
     * app by intent is a merchant-initiated one as far as the network is concerned — that is
     * what the intent flow is *for*, whoever the payee turns out to be — and that route wants
     * a reference unique to the attempt. The caller must make it so: a retry that reuses one
     * reads as a duplicate and is refused.
     *
     * It is filled in only when the code carried no reference of its own, because a real
     * merchant reconciles against theirs and it must not be displaced.
     */
    fun candidates(request: UpiPaymentRequest, amount: Amount, reference: String): List<String> {
        val query = query(request, amount, fallbackReference = reference)
        return (listOf(STANDARD) + APP_SCHEMES).map { "$it?$query" }
    }

    /**
     * Everything after the `?`.
     *
     * [fallbackReference] is used as `tr` only when the code did not carry one of its own —
     * the QR's reference always wins, because it is what the payee will reconcile against.
     */
    private fun query(
        request: UpiPaymentRequest,
        amount: Amount,
        fallbackReference: String? = null,
    ): String = buildString {
        append("pa=").append(encode(request.vpa.value))
        request.payeeName?.let { append("&pn=").append(encode(it)) }
        append("&am=").append(rupees(amount))
        // Rupees, always and explicitly. UPI accepts no other currency, and an app that
        // defaults differently would silently be asking for something else.
        append("&cu=INR")
        request.transactionNote?.let { append("&tn=").append(encode(it)) }
        request.merchantCode?.let { append("&mc=").append(encode(it)) }
        (request.transactionRef ?: fallbackReference)?.let { append("&tr=").append(encode(it)) }
        // Whatever else the code carried, handed back as it was found. See
        // UpiPaymentRequest.extras for why dropping these is not the harmless tidy-up it looks.
        request.extras.forEach { (key, value) ->
            append('&').append(encode(key)).append('=').append(encode(value))
        }
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

    /** What NPCI specifies, and the only one every UPI app is meant to answer. */
    private const val STANDARD = "upi://pay"

    /*
     * There is deliberately no placeholder merchant category here any more.
     *
     * `mc=0000` was tried, on the reading that an intent hand-off is a merchant payment and
     * NPCI wants the field. It changed nothing — the same payment was refused the same way
     * with it and without it — so what remains is a claim about the payment that Odo cannot
     * support, sent for no benefit. A code carrying its own `mc` still has it forwarded.
     */

    /**
     * The apps' own schemes, tried only when the standard link resolves to nothing.
     *
     * Google Pay's was read off a real handset — `tez://upi/pay` reaches its
     * `DeepLinkIntentFilter` on a phone where the `upi:` one is disabled. PhonePe's and
     * Paytm's are the forms both publish for merchant intent integration. Anything else stays
     * off this list until someone can point at a device where it answers.
     */
    private val APP_SCHEMES = listOf(
        "tez://upi/pay",
        "phonepe://pay",
        "paytmmp://pay",
    )
}
