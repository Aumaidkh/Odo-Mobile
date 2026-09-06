package com.hopcape.odo.feature.paywall.presentation.onetime

/** What the owner did on the one-time offers sheet. */
internal sealed interface OneTimeOffersEvent {

    /** A row was tapped. [productId] is the store's own id. */
    data class OfferTapped(val productId: String) : OneTimeOffersEvent

    /** The store could not be read and the owner asked again. */
    data object RetryTapped : OneTimeOffersEvent

    data object CloseTapped : OneTimeOffersEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface OneTimeOffersEffect {

    /** Close the sheet and go back to the plans underneath. */
    data object Dismiss : OneTimeOffersEffect
}
