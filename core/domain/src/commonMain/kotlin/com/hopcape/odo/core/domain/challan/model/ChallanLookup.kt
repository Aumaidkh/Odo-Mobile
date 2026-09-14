package com.hopcape.odo.core.domain.challan.model

/**
 * What a one-off lookup of an arbitrary plate came back with.
 *
 * A buyer's check, not the owner's record: the result is shown once and never persisted
 * (PRIVACY — "Nothing saved"), so it is a value, not an entity.
 *
 * [VehicleNotFound] is its own case rather than an error: a plate that is not in the
 * records is an *answer* (usually a typo, sometimes a brand-new vehicle), and the lookup
 * screen owes the owner that answer plus the ways to fix it — not a failure card.
 */
sealed interface ChallanLookup {

    /** The vehicle exists in the records; [challans] may legitimately be empty (clean). */
    data class Found(val challans: List<Challan>) : ChallanLookup

    /** No vehicle under that plate — say so, and offer the edit. */
    data object VehicleNotFound : ChallanLookup
}
