package com.hopcape.odo.core.triptracker

/** Read-only check; requesting permissions is the feature module's job. */
interface TrackingPreconditions {
    fun status(): TrackingReadiness
}
