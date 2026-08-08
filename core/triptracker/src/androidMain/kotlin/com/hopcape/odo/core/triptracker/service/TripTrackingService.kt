package com.hopcape.odo.core.triptracker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.triptracker.R
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.port.TripForegroundSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The trip-tracking foreground service. It holds no tracking *decision* logic of its own —
 * the running trip lives in the [TripTrackerEngine] singleton's own coroutine scope,
 * independent of this service's lifecycle. This service's job is the OS signal ("do not
 * kill this process while a trip is in progress") plus the live notification content (M5):
 * distance-so-far and car name, throttled so `notify()` isn't called on every GPS fix, and
 * routing the notification's Pause/Resume/Not-driving actions back to [TripTracker].
 */
internal class TripTrackingService : Service(), KoinComponent {

    private val tripTracker: TripTracker by inject()
    private val engine: TripTrackerEngine by inject()
    private val carRepository: CarRepository by inject()
    private val preconditions: TrackingPreconditions by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updatesJob: Job? = null
    @Volatile private var carName: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TripTrackingNotification.ensureChannel(this)
        scope.launch {
            carRepository.observePrimaryCar().collect { carName = it?.displayName }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routed = routeNotificationAction(intent?.action)
        if (routed != null) {
            scope.launch {
                when (routed) {
                    TripTrackerAction.PAUSE -> tripTracker.pauseActiveTrip()
                    TripTrackerAction.RESUME -> tripTracker.resumeActiveTrip()
                    TripTrackerAction.DISCARD -> tripTracker.discardActiveTrip()
                }
            }
            return START_STICKY
        }

        // A plain (no-action) start — TripForegroundSession.start() calling us fresh.
        val initial = if (preconditions.status().backgroundLocation) {
            TripTrackingNotification.buildLive(this, distanceMeters = 0, carName = carNameOrFallback(), isPaused = false)
        } else {
            TripTrackingNotification.buildFallback(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(TripTrackingNotification.NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(TripTrackingNotification.NOTIFICATION_ID, initial)
        }
        startLiveUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        updatesJob?.cancel()
        super.onDestroy()
    }

    private fun startLiveUpdates() {
        updatesJob?.cancel()
        updatesJob = scope.launch {
            var lastNotifiedDistance = -1L
            var lastNotifiedAtMs = 0L
            combine(engine.status, engine.distanceMeters) { status, distance -> status to distance }
                .collect { (status, distance) ->
                    val tracking = status as? TrackingStatus.Tracking ?: return@collect
                    val now = System.currentTimeMillis()
                    val distanceMilestone = lastNotifiedDistance < 0 || distance - lastNotifiedDistance >= DISTANCE_THROTTLE_M
                    val timeMilestone = now - lastNotifiedAtMs >= TIME_THROTTLE_MS
                    if (!distanceMilestone && !timeMilestone) return@collect
                    lastNotifiedDistance = distance
                    lastNotifiedAtMs = now
                    val notification = TripTrackingNotification.buildLive(
                        context = this@TripTrackingService,
                        distanceMeters = distance,
                        carName = carNameOrFallback(),
                        isPaused = tracking.isPaused,
                    )
                    NotificationManagerCompat.from(this@TripTrackingService).notify(TripTrackingNotification.NOTIFICATION_ID, notification)
                }
        }
    }

    private fun carNameOrFallback(): String = carName ?: getString(R.string.trip_tracking_notification_car_fallback)

    companion object {
        private const val DISTANCE_THROTTLE_M = 500L
        private const val TIME_THROTTLE_MS = 30_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TripTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripTrackingService::class.java))
        }
    }
}

/** [TripForegroundSession] over [TripTrackingService] — start/stop are fire-and-forget Intents. */
internal class AndroidTripForegroundSession(private val context: Context) : TripForegroundSession {
    override fun start() = TripTrackingService.start(context)
    override fun stop() = TripTrackingService.stop(context)
}
