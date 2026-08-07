package com.hopcape.odo.core.domain.trip.model

/** Where a trip stands in the confirm/reject flow. */
enum class TripStatus {
    /** Saved outright — passed the validation ladder with no low-confidence flags. */
    RECORDED,

    /** Saved but flagged for the owner to confirm — short, gap-heavy, or attribution-marginal. */
    NEEDS_CONFIRMATION,

    /** The owner confirmed a [NEEDS_CONFIRMATION] trip. */
    CONFIRMED,

    /** The owner rejected a [NEEDS_CONFIRMATION] trip. */
    REJECTED;

    /**
     * Whether this trip's distance moves the derived odometer. A trip still waiting on
     * the owner, or one they threw out, never did — low confidence never silent-saves.
     */
    val counted: Boolean get() = this == RECORDED || this == CONFIRMED
}
