package com.hopcape.odo.core.triptracker.motion

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.MotionSignal
import com.hopcape.odo.core.triptracker.port.MotionActivitySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The ActivityRecognition Transition API, as the [MotionActivitySource] port.
 *
 * [signals] is what the engine collects to arm/disarm the underlying transition updates
 * (§4.2's "debounced" motion depends on this actually being live). [events] is the same
 * flow [ActivityTransitionReceiver] emits into — a manifest-declared receiver isn't a
 * coroutine, so it can't return a `Flow` itself; this class is the bridge Koin resolves
 * for both sides.
 *
 * Every transition event here is already a settled decision from Play Services (that's
 * what "transition" means, as opposed to a raw confidence reading), so [MotionSignal.confidence]
 * is reported as a flat maximum rather than a real per-sample number.
 */
internal class TransitionMotionSource(private val context: Context) : MotionActivitySource {

    private val client = ActivityRecognition.getClient(context)
    private val events = MutableSharedFlow<MotionSignal>(extraBufferCapacity = EVENT_BUFFER)

    override fun signals(): Flow<MotionSignal> = events
        .onStart { arm() }
        .onCompletion { disarm() }

    /** Called by [ActivityTransitionReceiver] on the activity types listed in [request]. */
    fun onTransition(kind: MotionKind, at: Instant = Clock.System.now()) {
        events.tryEmit(MotionSignal(kind, CONFIDENCE_SETTLED, at))
    }

    private suspend fun arm() {
        try {
            client.requestActivityTransitionUpdates(request(), pendingIntent()).await()
        } catch (e: SecurityException) {
            // ACTIVITY_RECOGNITION not granted — TrackingReadiness already reports this;
            // there is simply nothing to arm until the owner grants it.
        }
    }

    private fun disarm() {
        client.removeActivityTransitionUpdates(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java).setAction(ACTION_TRANSITION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun request(): ActivityTransitionRequest {
        val kinds = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.ON_FOOT)
        val transitions = kinds.map {
            ActivityTransition.Builder()
                .setActivityType(it)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
        return ActivityTransitionRequest(transitions)
    }

    companion object {
        const val ACTION_TRANSITION = "com.hopcape.odo.core.triptracker.ACTIVITY_TRANSITION"
        private const val REQUEST_CODE = 4_202
        private const val EVENT_BUFFER = 8
        private const val CONFIDENCE_SETTLED = 100
    }
}

internal fun Int.toMotionKindOrNull(): MotionKind? = when (this) {
    DetectedActivity.IN_VEHICLE -> MotionKind.IN_VEHICLE
    DetectedActivity.STILL -> MotionKind.STILL
    DetectedActivity.WALKING -> MotionKind.WALKING
    DetectedActivity.ON_FOOT -> MotionKind.ON_FOOT
    else -> null
}
