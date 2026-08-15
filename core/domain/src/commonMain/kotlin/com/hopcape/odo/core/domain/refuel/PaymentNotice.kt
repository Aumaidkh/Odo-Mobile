package com.hopcape.odo.core.domain.refuel

import com.hopcape.odo.core.domain.shared.Amount
import kotlin.time.Instant

/**
 * One payment the phone was told about, reduced to the three things detection needs.
 *
 * This is the whole surface between the platform listener and the domain, and it is
 * deliberately this small. The listener sees every notification the phone shows; what leaves
 * it is a merchant name, a sum and a timestamp — nothing about who anyone messaged, nothing
 * about any other app, and nothing that is kept.
 *
 * [amount] is nullable because a notification does not always name a sum, and a fill with a
 * guessed amount is worse than one the owner types the amount into.
 */
data class PaymentNotice(
    /** The package the notification came from, for the per-app toggles. */
    val sourcePackage: String,
    /** The merchant as the payment app wrote it — "Bharat Petroleum, Karol Bagh". */
    val merchant: String,
    val amount: Amount?,
    val postedAt: Instant,
)
