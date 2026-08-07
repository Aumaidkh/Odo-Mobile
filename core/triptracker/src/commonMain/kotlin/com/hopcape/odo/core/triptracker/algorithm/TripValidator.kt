package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import kotlin.time.Duration

/** [status] is `null` exactly when [save] is `false` — a discarded trip has no status. */
internal data class Verdict(val save: Boolean, val status: TripStatus?)

/**
 * The reordered validation ladder (v3 bug #2 fixed): the biggest, most confident bucket
 * is checked first, so a trip that clears it is never later downgraded by a smaller
 * bucket it also happens to match.
 */
internal class TripValidator(private val config: TripTrackerConfig) {

    fun validate(
        distanceMeters: Long,
        estimatedMeters: Long,
        duration: Duration,
        attributionMarginal: Boolean,
    ): Verdict {
        val ladderStatus = when {
            distanceMeters >= config.recordedMinDistanceM -> TripStatus.RECORDED
            distanceMeters >= config.confirmMinDistanceM && duration >= config.confirmMinDuration ->
                TripStatus.RECORDED
            distanceMeters >= config.discardBelowDistanceM -> TripStatus.NEEDS_CONFIRMATION
            else -> return Verdict(save = false, status = null)
        }

        // Low confidence never silent-saves: a heavily gap-filled or attribution-marginal
        // trip is downgraded regardless of how big it otherwise looks.
        val estimatedRatio = if (distanceMeters == 0L) 0.0 else estimatedMeters.toDouble() / distanceMeters
        val downgraded = ladderStatus == TripStatus.RECORDED &&
            (estimatedRatio > config.estimatedPortionDowngradeRatio || attributionMarginal)

        return Verdict(save = true, status = if (downgraded) TripStatus.NEEDS_CONFIRMATION else ladderStatus)
    }
}
