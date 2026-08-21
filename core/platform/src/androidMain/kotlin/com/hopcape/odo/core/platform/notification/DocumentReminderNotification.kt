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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The document-expiry notification: its channel, its text and its posting.
 *
 * The type names are repeated here rather than read from the document vault's strings. A
 * notification is drawn by the system from Android resources, and the vault's labels are
 * Compose multiplatform resources, which the system cannot reach.
 */
internal object DocumentReminderNotification {

    const val CHANNEL_ID = "document_reminders"

    /** @return whether the notification was actually posted. False when the owner said no. */
    fun show(
        context: Context,
        documentId: String,
        typeName: String,
        daysBefore: Int,
        expiresOn: String,
    ): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)

        val label = context.getString(labelFor(typeName))
        val title = when (daysBefore) {
            1 -> context.getString(R.string.document_reminder_title_tomorrow, label)
            else -> context.getString(R.string.document_reminder_title_days, label, daysBefore)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.document_reminder_body, formatDate(expiresOn)))
            .setSmallIcon(R.drawable.ic_notification_odo)
            .setColor(ContextCompat.getColor(context, R.color.document_reminder_accent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        // One id per document and lead, so a renewal reminder does not replace the
        // "30 days left" one the owner has not read yet.
        NotificationManagerCompat.from(context)
            .notify("$documentId:$daysBefore".hashCode(), notification)
        return true
    }

    /**
     * Whether the app may post at all. From Android 13 the owner has to grant it, and posting
     * without the permission throws.
     */
    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.document_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.document_reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Opens the app. The vault is one tap in, and a deep link would need a route the OS owns. */
    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** "26 Jun 2026" — the date as the owner would read it off the paper. */
    private fun formatDate(isoDate: String): String = runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }.getOrDefault(isoDate)

    private fun labelFor(typeName: String): Int = when (typeName) {
        "INSURANCE" -> R.string.document_kind_insurance
        "PUC" -> R.string.document_kind_puc
        "RC" -> R.string.document_kind_rc
        "LICENCE" -> R.string.document_kind_licence
        "LOAN" -> R.string.document_kind_loan
        else -> R.string.document_kind_other
    }
}
