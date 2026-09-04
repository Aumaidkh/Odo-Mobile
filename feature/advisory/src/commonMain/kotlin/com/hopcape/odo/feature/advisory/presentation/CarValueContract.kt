package com.hopcape.odo.feature.advisory.presentation

/** What the owner did on the value screen. */
internal sealed interface CarValueEvent {

    /** "Scan first bill" — the one action that changes the number on this screen. */
    data object ScanClicked : CarValueEvent

    /**
     * "Share" — hand the summary to whatever the platform offers.
     *
     * [text] is assembled by the screen because it is copy with formatted figures in it,
     * and both only exist in composition. The plate and the workshop are deliberately
     * absent from it: a share card never carries either.
     */
    data class ShareClicked(val text: String) : CarValueEvent

    data object BackClicked : CarValueEvent
}

/**
 * One-shot things that happen outside the screen's own state.
 *
 * All data: the ViewModel decides *what* should happen and the route turns it into a
 * navigation or platform command, so presentation stays free of both.
 */
internal sealed interface CarValueEffect {

    data object NavigateBack : CarValueEffect

    data object OpenScanner : CarValueEffect

    /** [text] is already assembled — the plate and the workshop are deliberately not in it. */
    data class Share(val text: String) : CarValueEffect
}
