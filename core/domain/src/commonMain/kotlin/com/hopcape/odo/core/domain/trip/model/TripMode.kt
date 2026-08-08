package com.hopcape.odo.core.domain.trip.model

/** How confidently a trip was attributed to the car. */
enum class TripMode {
    /** The bonded Bluetooth device was connected for the whole trip — the strongest evidence. */
    BT_VERIFIED,

    /** No bond presence; attributed by sustained in-vehicle motion starting at the parked location. */
    GPS_ONLY,

    /**
     * Not observed directly — the segment between two known points the car covered while
     * unwatched, filled in from a route estimate rather than a live fix.
     */
    GAP_INFERRED,
}
