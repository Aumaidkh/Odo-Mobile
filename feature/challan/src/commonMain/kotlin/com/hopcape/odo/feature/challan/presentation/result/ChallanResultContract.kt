package com.hopcape.odo.feature.challan.presentation.result

/** What the buyer did on the lookup result, as data. */
internal sealed interface ChallanResultEvent {

    data object BackTapped : ChallanResultEvent

    data object RefreshTapped : ChallanResultEvent

    /** "Check another number" — back to the lookup, input intact. */
    data object CheckAnotherTapped : ChallanResultEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ChallanResultEffect {

    data object NavigateBack : ChallanResultEffect
}
