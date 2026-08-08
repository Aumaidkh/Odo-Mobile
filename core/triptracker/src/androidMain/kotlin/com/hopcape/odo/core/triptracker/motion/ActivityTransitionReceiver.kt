package com.hopcape.odo.core.triptracker.motion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Manifest-declared (not dynamically registered — see this module's `AndroidManifest.xml`
 * for why) target for [TransitionMotionSource]'s `PendingIntent`. Resolves its dependency
 * through [KoinComponent] rather than a constructor, the same reason
 * [OdoSyncWorker][com.hopcape.odo.core.platform.sync.OdoSyncWorker] does: the OS
 * instantiates receivers itself, so there is no other wiring path.
 *
 * Does exactly one thing — hand the event to [TransitionMotionSource]. Starting the
 * foreground service, if this settles into a trip start, is the engine's decision
 * (`TripEffect.StartForegroundSession`), not this receiver's.
 */
internal class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val motionSource: TransitionMotionSource by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TransitionMotionSource.ACTION_TRANSITION) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            val kind = event.activityType.toMotionKindOrNull() ?: continue
            motionSource.onTransition(kind)
        }
    }
}
