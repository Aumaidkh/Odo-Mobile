package com.hopcape.odo.feature.billscanner.presentation.pay

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.payment.model.UpiPaymentRequest
import com.hopcape.odo.feature.billscanner.presentation.state.Submission

/** Which half of the flow the screen is showing. */
internal enum class PayStage {

    /** Confirm who is being paid and how much, then hand off to a UPI app. */
    Paying,

    /** The money moved; record what it bought so the mileage can be measured. */
    Logging,
}

/**
 * Display state for scan-to-pay.
 *
 * One screen with two stages rather than two destinations, because it is one errand: the
 * owner is standing at a pump, and a back stack that lets them return to "pay" after paying
 * is a back stack that invites a second payment.
 */
@Immutable
internal data class PayAtPumpUiState(
    val stage: PayStage = PayStage.Paying,
    val submission: Submission = Submission.Idle,
    /** Null while the QR is being parsed, or when it was not a payment code at all. */
    val request: UpiPaymentRequest? = null,
    /** Rupees as typed. Kept as text so a half-typed "12." is not rounded away underneath. */
    val amount: String = "",
    val odometer: String = "",
    val litres: String = "",
    val station: String = "",
) {
    /** Whether the payment can be launched. */
    val canPay: Boolean
        get() = !submission.isInFlight && request != null && (amountPaise ?: 0) > 0

    /** Whether the fill can be written. Odometer and quantity are both required — see the use case. */
    val canSaveFill: Boolean
        get() = !submission.isInFlight && odometer.toIntOrNull() != null && quantityMilli != null

    /**
     * The typed rupees as integer paise, or null when it is not a plain figure.
     *
     * Parsed here, in text, rather than through a `Double`: money is integer paise everywhere
     * in this app precisely so rounding can never happen behind anyone's back.
     */
    val amountPaise: Long? get() = parseDecimal(amount, places = 2)

    /** The typed litres as thousandths, or null when it is not a plain figure. */
    val quantityMilli: Long? get() = parseDecimal(litres, places = 3)
}

/**
 * `"32.45"` to `32450` at three decimal places, `"104.5"` to `10450` at two.
 *
 * Returns null for anything that is not digits with at most one separator, so a typo shows as
 * a disabled button rather than as a silently wrong amount.
 */
private fun parseDecimal(raw: String, places: Int): Long? {
    val text = raw.trim().replace(',', '.')
    if (text.isEmpty()) return null
    val whole = text.substringBefore('.')
    val fraction = text.substringAfter('.', missingDelimiterValue = "")
    if (whole.isEmpty() || !whole.all { it.isDigit() }) return null
    if (fraction.length > places || !fraction.all { it.isDigit() }) return null
    val scale = when (places) { 2 -> 100L; 3 -> 1_000L; else -> return null }
    val wholeValue = whole.toLongOrNull() ?: return null
    return wholeValue * scale + (fraction.padEnd(places, '0').toLongOrNull() ?: return null)
}

/** What the owner did on the pay screen, as data. */
internal sealed interface PayAtPumpEvent {
    data class AmountChanged(val value: String) : PayAtPumpEvent
    data class OdometerChanged(val value: String) : PayAtPumpEvent
    data class LitresChanged(val value: String) : PayAtPumpEvent
    data class StationChanged(val value: String) : PayAtPumpEvent
    data object PayTapped : PayAtPumpEvent

    /** The UPI app came back. [response] is its raw answer, null when nothing was returned. */
    data class PaymentReturned(val response: String?) : PayAtPumpEvent

    /** The hand-off could not happen at all. */
    data class PaymentUnavailable(val onThisPlatform: Boolean) : PayAtPumpEvent

    data object SaveFillTapped : PayAtPumpEvent
    data object BackTapped : PayAtPumpEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface PayAtPumpEffect {

    /** Open a UPI app with this `upi://pay?…` link. */
    data class LaunchUpi(val link: String) : PayAtPumpEffect

    /** The fill was written; the flow is done. */
    data object FillSaved : PayAtPumpEffect

    data object NavigateBack : PayAtPumpEffect
}
