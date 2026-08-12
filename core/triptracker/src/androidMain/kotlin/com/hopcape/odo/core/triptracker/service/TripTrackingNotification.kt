package com.hopcape.odo.core.triptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.hopcape.odo.core.triptracker.R

/**
 * The trip-tracking foreground service's notification (M5). [buildLive] is the normal
 * "Trip in progress" content with Pause/Resume + Not driving actions; [buildFallback] is
 * shown instead when a trigger fires but background location is missing — there is no fix
 * stream to report distance from, so the notification asks the owner to open the app
 * instead of claiming a session that has no location behind it.
 */
internal object TripTrackingNotification {

    const val CHANNEL_ID = "trip_tracking"
    const val NOTIFICATION_ID = 4_201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val name = context.getString(R.string.trip_tracking_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun buildLive(context: Context, distanceMeters: Long, carName: String, isPaused: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.trip_tracking_notification_title))
            .setContentText(context.getString(R.string.trip_tracking_notification_text, formatDistance(context, distanceMeters), carName))
            .setSubText(context.getString(R.string.trip_tracking_notification_subtext, carName))
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setLargeIcon(largeIcon(context))
            .setColor(ContextCompat.getColor(context, R.color.trip_tracking_accent))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(pauseOrResumeAction(context, isPaused))
            .addAction(discardAction(context))
            .setContentIntent(openAppIntent(context))
            .build()

    fun buildFallback(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.trip_tracking_channel_name))
            .setContentText(context.getString(R.string.trip_tracking_fallback_notification_text))
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setColor(ContextCompat.getColor(context, R.color.trip_tracking_accent))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .build()

    /** The status-avatar bitmap [buildLive]'s large icon needs — vector, rasterized once per build. */
    private fun largeIcon(context: Context) =
        ContextCompat.getDrawable(context, R.drawable.ic_notification_trip_large)?.toBitmap()

    private fun pauseOrResumeAction(context: Context, isPaused: Boolean): NotificationCompat.Action {
        val action = if (isPaused) TripTrackingActions.ACTION_RESUME else TripTrackingActions.ACTION_PAUSE
        val label = if (isPaused) R.string.trip_tracking_notification_action_resume else R.string.trip_tracking_notification_action_pause
        val requestCode = if (isPaused) REQUEST_CODE_RESUME else REQUEST_CODE_PAUSE
        return NotificationCompat.Action.Builder(0, context.getString(label), servicePendingIntent(context, action, requestCode)).build()
    }

    private fun discardAction(context: Context): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0,
        context.getString(R.string.trip_tracking_notification_action_not_driving),
        servicePendingIntent(context, TripTrackingActions.ACTION_DISCARD, REQUEST_CODE_DISCARD),
    ).build()

    /** Targets [TripTrackingService] directly — same component, `onStartCommand` routes it (no `BroadcastReceiver` needed). */
    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TripTrackingService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getForegroundService(context, requestCode, intent, flags)
    }

    /** Tapping the notification body (live or fallback) just foregrounds the app — no deep link into a specific screen. */
    private fun openAppIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_CODE_OPEN_APP, launchIntent, flags)
    }

    private fun formatDistance(context: Context, distanceMeters: Long): String =
        context.getString(R.string.trip_tracking_notification_distance_km, distanceMeters / 1000.0)

    private const val REQUEST_CODE_PAUSE = 4_211
    private const val REQUEST_CODE_RESUME = 4_212
    private const val REQUEST_CODE_DISCARD = 4_213
    private const val REQUEST_CODE_OPEN_APP = 4_214
}
