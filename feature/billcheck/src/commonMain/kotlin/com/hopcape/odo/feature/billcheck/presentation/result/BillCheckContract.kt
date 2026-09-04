package com.hopcape.odo.feature.billcheck.presentation.result

/** What the owner did on the bill check result. */
internal sealed interface BillCheckEvent {

    data object BackClicked : BillCheckEvent

    /**
     * "Share" — hand the findings to whatever the platform offers.
     *
     * [text] is assembled by the screen because it is copy with formatted figures in it, and
     * both only exist in composition. The plate and the workshop name are deliberately absent
     * from it: a share card never carries either.
     */
    data class ShareClicked(val text: String) : BillCheckEvent

    /** "How we know" — open the band's basis for the line it was read from. */
    data object HowWeKnowClicked : BillCheckEvent

    /** The nudge shown while there is no record to compare against. */
    data object AddLastBillClicked : BillCheckEvent

    /** Any tap on the wall, including the wall's own way through. */
    data object UnlockClicked : BillCheckEvent

    data object RetryClicked : BillCheckEvent

    /**
     * The screen came back to the front — the offers sheet over it was dismissed.
     *
     * The wall is the reason this exists: a check bought on that sheet has to unmask the
     * findings behind it, and nothing else on this screen would ever ask again.
     */
    data object Resumed : BillCheckEvent
}

/**
 * One-shot things that happen outside the screen's own state.
 *
 * All data: the ViewModel decides *what* should happen and the route turns it into a
 * navigation or platform command, so presentation stays free of both.
 */
internal sealed interface BillCheckEffect {

    data object NavigateBack : BillCheckEffect

    /** [text] is already assembled — the plate and the workshop are deliberately not in it. */
    data class Share(val text: String) : BillCheckEffect

    /** Open "How we know" for [lineName]. */
    data class OpenBasis(val lineName: String) : BillCheckEffect

    /** Open the one-time offers, framed as the bill-check wall. */
    data object OpenOffers : BillCheckEffect

    /** Send the owner to add the bill that would let repeats be flagged. */
    data object AddLastBill : BillCheckEffect
}
