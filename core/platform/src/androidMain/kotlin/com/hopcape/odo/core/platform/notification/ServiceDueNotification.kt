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
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminder
import com.hopcape.odo.core.platform.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The service-due notification, on its own channel so it can be muted apart from documents. */
internal object ServiceDueNotification {

    private const val CHANNEL_ID = "service_reminders"

    /** @return whether the notification was posted. False when the owner said no. */
    fun show(context: Context, kind: ServiceReminder.Kind, dueOn: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(context)

        val (title, body) = when (kind) {
            ServiceReminder.Kind.DUE_SOON -> R.string.service_reminder_due_soon_title to R.string.service_reminder_due_soon_body
            ServiceReminder.Kind.OVERDUE -> R.string.service_reminder_overdue_title to R.string.service_reminder_overdue_body
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(body, formatDate(dueOn)))
            .setSmallIcon(R.drawable.ic_notification_odo)
            .setColor(ContextCompat.getColor(context, R.color.document_reminder_accent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify("service:${kind.name}".hashCode(), notification)
        return true
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.service_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.service_reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.notificationLaunchIntent(NotificationOpen.SERVICE_DUE) ?: return null
        return PendingIntent.getActivity(
            context,
            NotificationOpen.requestCode(NotificationOpen.SERVICE_DUE),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatDate(isoDate: String): String = runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }.getOrDefault(isoDate)
}
