package com.hopcape.odo.core.domain.payment

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import com.hopcape.odo.core.domain.payment.model.UpiPaymentRequest
import com.hopcape.odo.core.domain.payment.model.UpiVpa
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Turns the string inside a payment QR into a [UpiPaymentRequest].
 *
 * A pure function in the shared kernel because this is a *format*, not an integration: the
 * NPCI deep link is a documented spec, and parsing it needs no camera, no network and no
 * clock. That also means every odd real-world code — a missing amount, an encoded merchant
 * name, an extra parameter nobody documented — can be covered by a test.
 *
 * Only the `upi://pay?...` form is accepted. Codes that carry an EMVCo/Bharat QR payload (a
 * numeric tag-length-value string) are **rejected** rather than guessed at: they encode the
 * same payment in a different grammar, and misreading one would send money to the wrong
 * place. That is a scan that reports [DomainError.UnsupportedQr], not one that half-works.
 */
object UpiQrParser {

    private const val SCHEME = "upi://"
    private const val PAYEE_ADDRESS = "pa"
    private const val PAYEE_NAME = "pn"
    private const val AMOUNT = "am"
    private const val NOTE = "tn"
    private const val MERCHANT_CODE = "mc"
    private const val TRANSACTION_REF = "tr"

    /**
     * The parameters this app has a field for. Everything else is either forwarded or dropped.
     *
     * The currency is here but has no field: it is always INR, and it is rebuilt rather than
     * echoed so a code claiming anything else cannot carry that claim through.
     */
    private val MODELLED = setOf(PAYEE_ADDRESS, PAYEE_NAME, AMOUNT, NOTE, MERCHANT_CODE, TRANSACTION_REF, "cu")

    /**
     * The rest of the NPCI deep-link grammar — carried through untouched, and nothing else.
     *
     * An allowed list rather than a blocked one, and that distinction is the whole point. A
     * merchant QR is validated as a unit: drop its `sign` and the payment it authorises is no
     * longer the payment being made, so these have to survive. But a QR may also carry fields
     * belonging to the app that printed it — Google Pay's codes carry `aid`, an attribution
     * token meaning something only inside Google Pay — and handing one of those back from a
     * different app is a foreign app replaying a token it was never issued. Payment apps check
     * for exactly that, and they do not report it as a bad link: Google Pay says the bank
     * refused for exceeding a limit, on a payment of one rupee.
     *
     * So: everything the spec defines is forwarded, anything an app invented for itself is
     * left behind. A field added to the spec later needs adding here, and until then it is
     * dropped — which is the safe direction, because the payment still describes itself
     * completely without it.
     */
    private val FORWARDABLE = setOf(
        "tid", "mam", "url", "mode", "purpose", "orgid", "sign", "msid", "mtid", "refurl",
    )

    /** Parse [payload] — whatever the scanner read out of the code. */
    fun parse(payload: String?): Either<DomainError, UpiPaymentRequest> {
        val raw = payload?.trim().orEmpty()
        if (!raw.startsWith(SCHEME, ignoreCase = true)) return DomainError.UnsupportedQr.left()

        val params = queryParameters(raw)
        return either {
            val vpa = UpiVpa.of(params[PAYEE_ADDRESS]).bind()
            UpiPaymentRequest(
                vpa = vpa,
                payeeName = params[PAYEE_NAME]?.takeIf { it.isNotBlank() },
                amount = parseRupees(params[AMOUNT]).bind(),
                transactionNote = params[NOTE]?.takeIf { it.isNotBlank() },
                merchantCode = params[MERCHANT_CODE]?.takeIf { it.isNotBlank() },
                transactionRef = params[TRANSACTION_REF]?.takeIf { it.isNotBlank() },
                extras = params
                    .filterKeys { it !in MODELLED && it in FORWARDABLE }
                    .filterValues { it.isNotBlank() },
            )
        }
    }

    /**
     * The query string as a map.
     *
     * Later duplicates lose to earlier ones. A code carrying two `pa` values is malformed,
     * and taking the first keeps the answer stable rather than depending on ordering.
     *
     * The code's own ordering is preserved, because the unmodelled parameters are handed back
     * to a payment app as they were found and there is no reason to shuffle them.
     */
    private fun queryParameters(uri: String): Map<String, String> =
        uri.substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val key = pair.substringBefore('=').lowercase()
                val value = pair.substringAfter('=', missingDelimiterValue = "")
                if (key.isEmpty()) null else key to percentDecode(value)
            }
            .fold(LinkedHashMap<String, String>()) { seen, (key, value) ->
                seen.also { if (key !in it) it[key] = value }
            }

    /**
     * Rupees as printed in the code (`"104.50"`) to integer paise.
     *
     * Parsed as text, digit by digit, never through a `Double`. `104.50` is not exactly
     * representable in binary floating point, and money in this app is integer paise
     * precisely so that rounding can never happen behind anyone's back.
     *
     * An absent amount is not an error: most fuel-pump codes leave the sum to the payer.
     */
    private fun parseRupees(raw: String?): Either<DomainError, Amount?> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Either.Right(null)

        val rupees = text.substringBefore('.')
        val fraction = text.substringAfter('.', missingDelimiterValue = "")
        val wellFormed = rupees.isNotEmpty() &&
            rupees.all { it.isDigit() } &&
            fraction.length <= 2 &&
            fraction.all { it.isDigit() }
        if (!wellFormed) return DomainError.InvalidUpiAmount.left()

        val paise = fraction.padEnd(2, '0')
        val total = rupees.toLongOrNull()?.let { it * 100 + paise.toLong() }
            ?: return DomainError.InvalidUpiAmount.left()
        return Amount.of(total)
    }

    /**
     * Undo the percent-encoding a QR generator applied to names and notes.
     *
     * Hand-rolled because Kotlin common has no URL decoder, and pulling one in for this
     * would be a dependency for twenty lines. `+` is left alone deliberately: it means a
     * plus sign in a URI path or query built to spec, and turning it into a space would
     * corrupt merchant names that legitimately contain one.
     */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3).toIntOrNull(radix = 16)
                if (hex != null) {
                    bytes += hex.toByte()
                    index += 3
                    continue
                }
            }
            // A stray '%' that is not an escape stays as it was typed.
            char.toString().encodeToByteArray().forEach { bytes += it }
            index++
        }
        return bytes.toByteArray().decodeToString()
    }
}
