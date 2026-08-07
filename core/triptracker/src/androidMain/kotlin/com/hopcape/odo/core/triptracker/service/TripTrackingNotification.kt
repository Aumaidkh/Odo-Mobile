package com.hopcape.odo.core.triptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hopcape.odo.core.triptracker.R

/** The trip-tracking foreground service's one, low-importance notification. */
internal object TripTrackingNotification {

    const val CHANNEL_ID = "trip_tracking"
    const val NOTIFICATION_ID = 4_201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val name = context.getString(R.string.trip_tracking_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.trip_tracking_notification_title))
            .setContentText(context.getString(R.string.trip_tracking_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
