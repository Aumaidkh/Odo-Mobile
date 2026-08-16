package com.hopcape.odo.core.domain.subscription

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * What state a live subscription is in.
 *
 * Separate from [Plan] because these are different questions with different audiences. The
 * plan decides what the owner may do, and every gate in the app reads it. This decides what
 * the profile card says, and only the profile card reads it.
 */
enum class SubscriptionHealth {

    /** Paid, renewing, nothing to say. */
    ACTIVE,

    /** Inside the free trial. The first charge has not happened yet. */
    IN_TRIAL,

    /**
     * A renewal failed and the store is retrying the payment method.
     *
     * Pro still works — Play keeps the entitlement live through the grace period — but the
     * owner has a card to fix, and they will not find out from the store's email if they do
     * not read it.
     */
    BILLING_ISSUE,

    /**
     * Cancelled, and running out.
     *
     * Pro works until the date and then stops. Worth saying plainly rather than letting it
     * lapse silently: someone who cancelled by accident has until then to change their mind.
     */
    CANCELLED,
}

/**
 * A live subscription, as the profile card describes it.
 *
 * Only exists while there is one — a free owner has no status, which is `null` rather than a
 * state meaning "none". There is nothing to say about a subscription that does not exist.
 */
data class SubscriptionState(

    /** Which plan is being paid for. */
    val period: BillingPeriod,

    /** What state it is in. */
    val health: SubscriptionHealth,

    /**
     * The day it next charges, or the day it stops when [health] is
     * [SubscriptionHealth.CANCELLED]. Null when the store did not give one.
     */
    val renewsOn: LocalDate?,

    /**
     * Where the owner manages or cancels it — the store's own subscription page.
     *
     * The store's, and only the store's. Cancelling has to happen there: it is what Play
     * requires, and an in-app cancel that did not actually cancel is the worst version of
     * this screen. Null when the store did not supply one, and the button is hidden.
     */
    val managementUrl: String?,
)

/**
 * Port over the details of the owner's subscription.
 *
 * Deliberately not part of `EntitlementSource`. That port answers what the owner may do and
 * is read by every gate in the app; this one answers what their plan is doing and is read by
 * one card. Merging them would put a renewal date in front of code that only wanted to know
 * whether to draw a lock.
 */
fun interface SubscriptionStatusSource {

    /** The live subscription, or `null` when there is none. */
    fun observe(): Flow<SubscriptionState?>
}
