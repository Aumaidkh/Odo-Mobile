package com.hopcape.odo.core.platform.notification

/**
 * Pulls the merchant and the sum out of a payment app's notification text.
 *
 * Payment apps write one sentence and vary it slightly: "You paid ₹2,000 to Bharat Petroleum,
 * Karol Bagh", "Paid Rs. 2000 to HP Petrol Pump", "$45.20 sent to Shell". What is stable
 * across all of them is that the amount is a number next to a currency mark, and the merchant
 * is whatever follows the word that means "to".
 *
 * In `commonMain` rather than beside the Android service, because this is the part worth
 * testing and none of it needs a platform. The service is a callback that hands text here.
 *
 * Nothing read here is stored or sent. It exists so the classifier can be asked a question
 * about a merchant name, and the answer decides whether the owner is shown anything at all.
 */
object PaymentNoticeReader {

    /**
     * The merchant, or `null` when the sentence has no recipient in it.
     *
     * `null` is the right answer for most notifications a phone shows — a message, a delivery
     * update, a low-battery warning. Detection needs a name it can classify, and no name means
     * nothing to classify.
     */
    fun merchantIn(text: String): String? {
        val match = RECIPIENT.find(text) ?: return null
        val merchant = match.groupValues[2]
            .trim()
            .trimEnd('.', '!', '·', '-')
            .trim()
        return merchant.takeIf { it.length >= MIN_MERCHANT_LENGTH }
    }

    /**
     * The amount in minor units — paise, cents — or `null` when no sum was named.
     *
     * A notification without an amount is not a failure. It stops detection from drafting a
     * fill, which is correct: a fill with a guessed amount is worse than one the owner types
     * the amount into themselves.
     *
     * Only a number sitting next to a currency mark counts. A bare number in a payment
     * sentence is as likely to be a card's last four digits or a reference number.
     */
    fun amountIn(text: String): Long? {
        val match = AMOUNT.find(text) ?: return null
        val digits = match.groupValues[1].replace(",", "")
        val parts = digits.split('.')
        val whole = parts[0].toLongOrNull() ?: return null
        val minor = when (val fraction = parts.getOrNull(1)) {
            null, "" -> 0L
            else -> fraction.take(2).padEnd(2, '0').toLongOrNull() ?: return null
        }
        val total = whole * 100 + minor
        return total.takeIf { it > 0 }
    }

    private const val MIN_MERCHANT_LENGTH = 2

    /**
     * "…to <merchant>" and its variants, up to the end of the sentence.
     *
     * The merchant runs to a full stop or the end of the text rather than to the first comma,
     * because the branch is part of the name: "Bharat Petroleum, Karol Bagh" and the same
     * brand in another neighbourhood are two merchants, and the owner rejecting one must not
     * silence the other.
     */
    private val RECIPIENT = Regex(
        """\b(to|at|paid to|sent to)\s+([^.\n]{2,60})""",
        RegexOption.IGNORE_CASE,
    )

    /** A number attached to a currency mark, in either order. */
    private val AMOUNT = Regex(
        """(?:₹|rs\.?|inr|\$|€|£)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
}
