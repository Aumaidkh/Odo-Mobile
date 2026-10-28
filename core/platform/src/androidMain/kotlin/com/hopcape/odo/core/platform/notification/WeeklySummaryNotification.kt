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
import com.hopcape.odo.core.common.notification.NotificationOpen
import com.hopcape.odo.core.common.notification.notificationLaunchIntent
import com.hopcape.odo.core.domain.trip.analysis.WeeklySummary
import com.hopcape.odo.core.platform.R
import java.util.Locale

/** The Sunday recap, on the drive summary's channel. Missing parts are left out. */
internal object WeeklySummaryNotification {

    private const val CHANNEL_ID = "drive_summary"
    private const val NOTIFICATION_ID = 4_401

    fun show(context: Context, summary: WeeklySummary): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(context)

        val parts = listOfNotNull(
            summary.km?.let { context.getString(R.string.weekly_summary_km, it) },
            summary.perKmPaise?.let { context.getString(R.string.weekly_summary_per_km, String.format(Locale.US, "%.1f", it / 100.0)) },
            summary.distancePerUnit?.let { context.getString(R.string.weekly_summary_mileage, String.format(Locale.US, "%.1f", it)) },
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.weekly_summary_title, parts.joinToString(" · ")))
            .setContentText(context.getString(R.string.weekly_summary_body))
            .setSmallIcon(R.drawable.ic_notification_odo)
            .setColor(ContextCompat.getColor(context, R.color.document_reminder_accent))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.drive_summary_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.drive_summary_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.notificationLaunchIntent(NotificationOpen.WEEKLY_SUMMARY) ?: return null
        return PendingIntent.getActivity(
            context,
            NotificationOpen.requestCode(NotificationOpen.WEEKLY_SUMMARY),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
