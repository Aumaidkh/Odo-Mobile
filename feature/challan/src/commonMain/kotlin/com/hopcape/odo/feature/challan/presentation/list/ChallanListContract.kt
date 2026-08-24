package com.hopcape.odo.feature.challan.presentation.list

/**
 * What the owner did on their car's challans screen, as data.
 *
 * Three of these mean "ask the records again" from three different screens of the same
 * route (the pending list's pill, the clean state's CTA, the source-down retry) — kept
 * separate because telemetry needs to know which screen convinced them to tap.
 */
internal sealed interface ChallanListEvent {

    data object BackTapped : ChallanListEvent

    /** The "Checked … · Refresh" pill. */
    data object RefreshTapped : ChallanListEvent

    /** The clean state's "Check again now". */
    data object CheckAgainTapped : ChallanListEvent

    /** The source-down state's "Try again". */
    data object TryAgainTapped : ChallanListEvent

    /** The pay CTA — payment itself happens on the official Parivahan site. */
    data object PayTapped : ChallanListEvent

    /** "I've already paid these" — the owner's claim, taken at their word. */
    data object AlreadyPaidTapped : ChallanListEvent

    /** The collapsed "Older · N challans" row. */
    data object OlderToggled : ChallanListEvent

    /** The source-down state's "Check on Parivahan instead". */
    data object OpenParivahanTapped : ChallanListEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ChallanListEffect {

    data object NavigateBack : ChallanListEffect

    /** Open the official Parivahan e-challan site in the browser. */
    data class OpenParivahan(val url: String) : ChallanListEffect
}
