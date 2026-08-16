package com.hopcape.odo.core.domain.subscription

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port over buying and recovering a subscription.
 *
 * Neither call returns what the owner is now entitled to, on purpose. Entitlement is read
 * through `EntitlementSource`, which is a stream, and a purchase pushes a new value onto it —
 * so a screen that gated on entitlement already updates without being told. Returning it here
 * as well would give callers two answers to one question, and they would diverge the first
 * time a purchase completed outside the app.
 */
interface SubscriptionPurchaser {

    /**
     * Take the owner through the store's purchase sheet for [planId].
     *
     * [planId] is a [PlanOption.id] from the current [Offer] — the plans are read and bought
     * through the same identifiers, so a paywall can never start a purchase for something it
     * did not show.
     *
     * Backing out is [DomainError.PaymentCancelled], not a failure. It is the most common
     * ending a paywall has, and it must not put an error in front of someone who simply
     * changed their mind.
     */
    suspend fun purchase(planId: String): Either<DomainError, Unit>

    /**
     * Look for a subscription this person already paid for and re-apply it.
     *
     * Every store requires this: the same account on a new phone, or after a reinstall, has
     * bought Pro and must be able to get it back without paying again.
     */
    suspend fun restore(): Either<DomainError, RestoreOutcome>
}

/** What looking for an earlier purchase turned up. */
sealed interface RestoreOutcome {

    /** A subscription was found and is active again. */
    data object ProRestored : RestoreOutcome

    /**
     * The store answered, and this account has nothing to restore.
     *
     * Not an error: the usual cause is tapping Restore on an account that never subscribed.
     * It needs its own plain sentence rather than a failure message, because nothing went
     * wrong.
     */
    data object NothingToRestore : RestoreOutcome
}
