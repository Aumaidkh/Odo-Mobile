package com.hopcape.odo.core.platform.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hopcape.odo.core.platform.R

/**
 * A nudge back into the app, as a notification.
 *
 * Its own channel, away from reminders: an owner who mutes "things I asked to be told about"
 * has said nothing about being nudged, and an owner who mutes being nudged must not lose the
 * PUC expiry with it. One switch cannot carry both sentences.
 */
internal object EngagementNudgeNotification {

    const val CHANNEL_ID = "nudges"

    /** @return whether the notification was actually posted. False when the owner said no. */
    fun show(context: Context, nudge: String, title: String, body: String): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification_odo)
            .setColor(ContextCompat.getColor(context, R.color.document_reminder_accent))
            // Below a reminder on purpose: a nudge is Odo's idea, not the owner's.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        // Keyed on the kind, so a second nudge of one kind replaces the unread first rather
        // than stacking. Odo has one thing to say, however many times it had the thought.
        NotificationManagerCompat.from(context).notify(nudge.hashCode(), notification)
        return true
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.nudge_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.nudge_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
