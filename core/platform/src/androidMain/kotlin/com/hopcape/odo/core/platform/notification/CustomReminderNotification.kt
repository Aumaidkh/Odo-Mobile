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
 * The owner's own reminder, as a notification.
 *
 * Its own channel, separate from document expiry: the two are muted for different reasons —
 * "stop telling me about my papers" and "stop telling me about the thing I asked to be told
 * about" are different sentences, and Android's per-channel switch is where an owner says
 * either one.
 *
 * The title is the owner's own words, so nothing here translates it. The body is generic for
 * the same reason: whatever a reminder is about, the app is what it is asking them to open.
 */
internal object CustomReminderNotification {

    const val CHANNEL_ID = "custom_reminders"

    /** @return whether the notification was actually posted. False when the owner said no. */
    fun show(context: Context, reminderId: String, title: String): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.custom_reminder_body))
            .setSmallIcon(R.drawable.ic_notification_odo)
            .setColor(ContextCompat.getColor(context, R.color.document_reminder_accent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        // Keyed on the reminder, so a repeating one replaces its own unread nudge rather
        // than stacking a fortnight of tyre-pressure checks in the shade.
        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
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
            context.getString(R.string.custom_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.custom_reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Opens the app, the same way the document reminder does. */
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
