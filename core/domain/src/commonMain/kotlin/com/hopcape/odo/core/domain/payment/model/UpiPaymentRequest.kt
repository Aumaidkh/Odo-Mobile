package com.hopcape.odo.core.domain.payment.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * A UPI address — `someone@bank`. The one field a payment cannot be made without.
 *
 * Validated rather than taken as a string because this is where the owner's money goes. The
 * check is deliberately structural (one `@`, both sides present, no spaces) and not an
 * attempt to know every valid handle: banks add handles constantly, and an app that rejects
 * a real VPA because its list is stale is worse than one that lets the UPI app reject it.
 */
@JvmInline
value class UpiVpa private constructor(val value: String) {

    /** The handle after the `@` — `okhdfcbank`, `ybl`, `paytm`. Shown, never matched against. */
    val handle: String get() = value.substringAfter('@')

    companion object {
        fun of(raw: String?): Either<DomainError, UpiVpa> {
            val trimmed = raw?.trim().orEmpty()
            val name = trimmed.substringBefore('@', missingDelimiterValue = "")
            val handle = trimmed.substringAfter('@', missingDelimiterValue = "")
            val wellFormed = name.isNotEmpty() &&
                handle.isNotEmpty() &&
                !handle.contains('@') &&
                trimmed.none { it.isWhitespace() }
            return if (wellFormed) UpiVpa(trimmed).right() else DomainError.InvalidUpiAddress.left()
        }
    }
}

/**
 * A payment a QR code is asking for.
 *
 * Mirrors the fields of the NPCI `upi://pay` deep link. [amount] is null on the QR codes
 * taped to most fuel pumps, which name the payee and leave the sum to the payer — so the
 * flow must be able to ask for it rather than assuming the code carried it.
 *
 * [payeeName] is what the code claims the merchant is called. It is displayed for
 * recognition and never trusted as identity: the VPA is the only thing that decides where
 * money goes, and a code can say anything it likes about who owns it.
 */
data class UpiPaymentRequest(
    val vpa: UpiVpa,
    val payeeName: String?,
    val amount: Amount? = null,
    val transactionNote: String? = null,
    /** The merchant category code, when the QR is a registered merchant's rather than a person's. */
    val merchantCode: String? = null,
    /** The merchant's own reference for this collection, echoed back in the response. */
    val transactionRef: String? = null,
) {
    /** True when the code named a sum, so the owner is confirming rather than typing one. */
    val hasAmount: Boolean get() = amount != null
}
