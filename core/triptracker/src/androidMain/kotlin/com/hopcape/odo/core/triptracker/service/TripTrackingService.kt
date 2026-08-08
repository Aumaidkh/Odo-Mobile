package com.hopcape.odo.core.triptracker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.hopcape.odo.core.triptracker.port.TripForegroundSession

/**
 * The trip-tracking foreground service. It holds no tracking logic of its own — the
 * running trip lives in the [com.hopcape.odo.core.triptracker.engine.TripTrackerEngine]
 * singleton's own coroutine scope, independent of this service's lifecycle. This service's
 * only job is the OS signal: "do not kill this process while a trip is in progress."
 */
internal class TripTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TripTrackingNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = TripTrackingNotification.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(TripTrackingNotification.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(TripTrackingNotification.NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
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
