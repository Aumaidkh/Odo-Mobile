package com.hopcape.odo.feature.paywall.presentation.onetime

/** What the owner did on the one-time offers sheet. */
internal sealed interface OneTimeOffersEvent {

    /** A row was tapped. [productId] is the store's own id. */
    data class OfferTapped(val productId: String) : OneTimeOffersEvent

    /** The store could not be read and the owner asked again. */
    data object RetryTapped : OneTimeOffersEvent

    /** The sheet's own text button. Only some contexts draw one — see [OneTimeContext.close]. */
    data object CloseTapped : OneTimeOffersEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface OneTimeOffersEffect {

    /** Close the sheet and go back to the plans underneath. */
    data object Dismiss : OneTimeOffersEffect

    /**
     * Close the sheet **and** the errand under it.
     *
     * Only for a context whose screen underneath cannot be usefully returned to. The bill
     * check is the one: masked, it offers the same wall the owner has just declined, and
     * re-reads every time the sheet closes over it. Their bill is logged either way, so the
     * place to leave them is the record, not a screen with nothing left to show.
     */
    data object DismissErrand : OneTimeOffersEffect
}
