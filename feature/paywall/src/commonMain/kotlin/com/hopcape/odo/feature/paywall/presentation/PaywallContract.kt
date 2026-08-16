package com.hopcape.odo.feature.paywall.presentation

/** What the owner did on the paywall, as data. */
internal sealed interface PaywallEvent {

    /** The close button. */
    data object CloseTapped : PaywallEvent

    /** A plan card. Carries the store's own package id, so nothing is guessed from position. */
    data class PlanSelected(val planId: String) : PaywallEvent

    /** The main CTA. */
    data object StartProTapped : PaywallEvent

    /** "Restore" in the top bar. */
    data object RestoreTapped : PaywallEvent

    /** Retry, after the offer failed to load. */
    data object RetryTapped : PaywallEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface PaywallEffect {

    /**
     * Close the paywall.
     *
     * Sent for the close button and for a completed purchase alike. There is no separate
     * "purchased" effect because there is nothing extra to do: the entitlement stream has
     * already unlocked the screen underneath, so the right ending is to get out of the way.
     */
    data object GoBack : PaywallEffect
}
