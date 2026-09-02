package com.hopcape.odo.feature.challan.presentation.lookup

/** What the owner did on the buyer's-check screen, as data. */
internal sealed interface ChallanLookupEvent {

    data object BackTapped : ChallanLookupEvent

    data class PlateChanged(val value: String) : ChallanLookupEvent

    data object CheckTapped : ChallanLookupEvent

    /** The not-found state's "Edit the number" — back to the input with it intact. */
    data object EditNumberTapped : ChallanLookupEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ChallanLookupEffect {

    data object NavigateBack : ChallanLookupEffect

    /** The plate exists — show its result. [regNo] travels normalized. */
    data class OpenResult(val regNo: String) : ChallanLookupEffect
}
