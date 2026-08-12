package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TriggerMode

/** Which signal sources [TripTrackerEngine.setEnabled] actually subscribes to. */
internal data class ArmedTriggers(val motion: Boolean, val presence: Boolean)

/**
 * `(bond's trigger mode, current readiness) -> what gets armed` (auto-odometer plan
 * §1.1/§6). A trigger whose own permission is missing never falls back to the other
 * trigger — an unarmed source beats one that silently mis-attributes trips.
 *
 * STEREO's presence trigger additionally needs [TrackingReadiness.fineLocation]: presence
 * connecting alone no longer starts a trip (the speed-gate confirm needs a fix stream to
 * gate on) — without fine location the source would just sit there collecting connect
 * events it can never turn into a trip, so it stays unarmed too. NO_STEREO's motion
 * trigger needs [TrackingReadiness.activityRecognition] — the transition API itself
 * requires it.
 *
 * No bond at all arms nothing: tracking is enabled but has no car to trigger on yet.
 */
internal object TriggerArming {
    fun compute(mode: TriggerMode?, readiness: TrackingReadiness): ArmedTriggers = when (mode) {
        null -> ArmedTriggers(motion = false, presence = false)
        TriggerMode.NO_STEREO -> ArmedTriggers(motion = readiness.activityRecognition, presence = false)
        TriggerMode.STEREO -> ArmedTriggers(motion = false, presence = readiness.bluetoothConnect && readiness.fineLocation)
    }
}
