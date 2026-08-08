package com.hopcape.odo.feature.autoodometer.domain.model

import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.VehicleBond

/**
 * What the garage card (M1) and the settings header (M7) both need to render themselves.
 *
 * One shape for both screens rather than two reads, because they answer the same
 * question — is auto-odometer set up, and if not, why show the card — and a use case
 * per consumer would let them disagree about it.
 *
 * @param readingCount how many odometer readings the owner has typed in so far — the
 *   garage card's "N times" copy.
 * @param bond the enrolled car-trigger identity, or `null` before enrollment.
 * @param enabled whether automatic tracking is turned on.
 * @param readiness which tracking preconditions (permissions) are currently granted.
 */
internal data class SetupState(
    val readingCount: Int,
    val bond: VehicleBond?,
    val enabled: Boolean,
    val readiness: TrackingReadiness,
)
