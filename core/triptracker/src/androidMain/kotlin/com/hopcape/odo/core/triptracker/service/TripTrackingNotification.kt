package com.hopcape.odo.core.triptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * The trip-tracking foreground service's one, low-importance notification.
 *
 * The copy is plain English hardcoded here rather than a string resource: this module has
 * no Compose UI and no other Android string resources, and adding an `androidMain` `res/`
 * source set for these two short, non-localized system-notification lines was judged not
 * worth the added build-config surface — a real trade-off, not an oversight, and one to
 * revisit if this module ever needs more than a fixed system string.
 */
internal object TripTrackingNotification {

    const val CHANNEL_ID = "trip_tracking"
    const val NOTIFICATION_ID = 4_201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Trip tracking", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Odo is tracking this drive")
            .setContentText("We'll log it automatically when you park")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
