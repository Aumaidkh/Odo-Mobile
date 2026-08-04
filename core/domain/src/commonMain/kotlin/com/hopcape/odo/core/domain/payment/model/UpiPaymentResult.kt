package com.hopcape.odo.core.domain.payment.model

/**
 * How a payment ended, as the UPI app reported it.
 *
 * [Pending] exists because UPI genuinely has that state: the app hands back "submitted" and
 * the bank settles afterwards. It must not be treated as success — a fuel fill logged
 * against a payment that later fails is a record of something that did not happen — and it
 * must not be treated as failure either, because the money may well have moved.
 */
enum class UpiPaymentStatus {

    /** The bank confirmed it. The only status a fuel fill may be written against. */
    Success,

    /** Submitted, not yet settled. The owner is told to check their bank app. */
    Pending,

    /** Declined, or the owner backed out. */
    Failed,
}

/**
 * The outcome of handing a [UpiPaymentRequest] to a UPI app.
 *
 * [transactionRef] and [transactionId] are the bank's identifiers for the payment. One of them
 * is kept on the fuel fill so a disputed entry can be traced back to a real transaction —
 * without either, a logged fill is only a claim.
 */
data class UpiPaymentResult(
    val status: UpiPaymentStatus,
    val transactionId: String? = null,
    val transactionRef: String? = null,
) {
    /** Whether anything may be recorded on the strength of this. */
    val isConfirmed: Boolean get() = status == UpiPaymentStatus.Success
}

/**
 * How a fuel fill was paid for.
 *
 * Recorded rather than assumed because it is what separates a fill Odo watched happen from
 * one the owner typed in afterwards, and only the first can carry a transaction reference.
 */
enum class PaymentMethod {

    /** Paid through Odo's QR flow. Carries a transaction reference. */
    UPI,

    CASH,

    CARD,

    /** The owner logged it later and did not say. */
    UNKNOWN,
}
