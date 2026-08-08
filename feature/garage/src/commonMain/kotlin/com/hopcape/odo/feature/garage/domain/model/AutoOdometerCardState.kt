package com.hopcape.odo.feature.garage.domain.model

/**
 * What the auto-odometer slot on the garage's home base shows, if anything.
 *
 * One slot, three states (auto-odometer plan §1's garage-card-visibility rule): nothing
 * until the owner has typed enough readings to make the pitch land, the "NEW" pitch card
 * once they have, and a compact status tile once auto-odometer is actually turned on. Set
 * up always wins over the reading count — an owner with one reading who has already
 * enrolled still sees the tile, not the card.
 */
internal sealed interface AutoOdometerCardState {

    /** Fewer than two manual readings and not set up yet — nothing to pitch with. */
    data object Hidden : AutoOdometerCardState

    /** At least two manual readings, not set up — the "NEW" pitch card (M1). */
    data class NotSetUp(val readingCount: Int) : AutoOdometerCardState

    /** A bond exists and tracking is on — the compact status tile that opens settings (M7). */
    data class SetUp(val monthlyKm: Long) : AutoOdometerCardState
}
