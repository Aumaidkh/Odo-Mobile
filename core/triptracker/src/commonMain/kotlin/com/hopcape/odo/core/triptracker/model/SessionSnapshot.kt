package com.hopcape.odo.core.triptracker.model

import kotlin.time.Instant

/**
 * The crash-resume journal for the trip in progress — everything
 * [com.hopcape.odo.core.triptracker.port.TripSessionStore] persists so a process kill
 * loses no more than the last unwritten milestone.
 *
 * Public, like [com.hopcape.odo.core.triptracker.port.TripSessionStore] itself: its real
 * implementation in `:infrastructure:database` has to see this type.
 */
data class SessionSnapshot(
    val phase: String,
    val carId: String?,
    val mode: String?,
    val startedAt: Instant?,
    val distanceMeters: Long,
    val estimatedMeters: Long,
    val lastFix: LocationSample?,
    val stitchDeadline: Instant?,
)
