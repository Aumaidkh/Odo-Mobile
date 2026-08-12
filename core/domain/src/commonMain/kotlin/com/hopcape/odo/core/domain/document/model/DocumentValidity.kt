package com.hopcape.odo.core.domain.document.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * How much life a document has left, resolved against a day.
 *
 * The rule that turns an expiry date into "valid / renew soon / lapsed" is the one every
 * renewal surface reads — the vault's status badge, the garage's document row, the
 * reminder engine's due window, the health score's documentation points — so it is
 * derived **here**, once, instead of each feature re-deciding what "expiring soon" means.
 *
 * Never stored: it is a function of the document and the day it is read on. See
 * [Document.validity].
 */
sealed interface DocumentValidity {

    /** Papers that never lapse (an RC, a closed loan letter) — nothing to renew. */
    data object NoExpiry : DocumentValidity

    /** In force, and not close enough to expiry to nag about. */
    data class Valid(val until: LocalDate, val daysLeft: Int) : DocumentValidity

    /** In force, but inside the renewal window — the state that earns a reminder. */
    data class ExpiringSoon(val until: LocalDate, val daysLeft: Int) : DocumentValidity

    /** Lapsed. Driving on a lapsed insurance or PUC is an offence in India, so this is
     *  surfaced loudly wherever it appears. */
    data class Expired(val since: LocalDate, val daysAgo: Int) : DocumentValidity

    /** True while the paper still covers the owner — the one question most callers ask. */
    val isInForce: Boolean get() = this !is Expired

    /** True when the owner should act now: lapsed, or about to lapse. */
    val needsAttention: Boolean get() = this is Expired || this is ExpiringSoon

    companion object {
        /**
         * How close to expiry counts as [ExpiringSoon]. Thirty days is the window Indian
         * insurers and PUC centres themselves nudge in, and it leaves room to book a slot.
         */
        const val RENEWAL_WINDOW_DAYS: Int = 30

        /**
         * @param expiresOn the document's expiry, or null for papers that never lapse.
         * @param today the day being read on — passed in, never read from a clock here,
         *   so the domain stays pure and the rule is trivially testable.
         */
        fun of(
            expiresOn: LocalDate?,
            today: LocalDate,
            renewalWindowDays: Int = RENEWAL_WINDOW_DAYS,
        ): DocumentValidity {
            if (expiresOn == null) return NoExpiry
            val days = today.daysUntil(expiresOn)
            return when {
                days < 0 -> Expired(since = expiresOn, daysAgo = -days)
                days <= renewalWindowDays -> ExpiringSoon(until = expiresOn, daysLeft = days)
                else -> Valid(until = expiresOn, daysLeft = days)
            }
        }
    }
}
