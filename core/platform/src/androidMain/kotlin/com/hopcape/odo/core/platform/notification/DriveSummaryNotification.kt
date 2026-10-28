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
import com.hopcape.odo.core.domain.trip.analysis.DailyDriveSummary
import com.hopcape.odo.core.platform.R

/** The evening drive summary, on its own low-importance channel. */
internal object DriveSummaryNotification {

    private const val CHANNEL_ID = "drive_summary"
    private const val NOTIFICATION_ID = 4_400

    /** @return whether it was posted. False when the owner said no. */
    fun show(context: Context, summary: DailyDriveSummary): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(context)

        val drives = context.resources.getQuantityString(R.plurals.drive_summary_drives, summary.drives, summary.drives)
        val title = summary.fuelEstimatePaise?.let { paise ->
            context.getString(R.string.drive_summary_title_cost, drives, summary.km, paise / 100)
        } ?: context.getString(R.string.drive_summary_title, drives, summary.km)
        val body = context.getString(
            if (summary.fuelEstimatePaise != null) R.string.drive_summary_body_estimate else R.string.drive_summary_body,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
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
        val intent = context.notificationLaunchIntent(NotificationOpen.DRIVE_SUMMARY) ?: return null
        return PendingIntent.getActivity(
            context,
            NotificationOpen.requestCode(NotificationOpen.DRIVE_SUMMARY),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
