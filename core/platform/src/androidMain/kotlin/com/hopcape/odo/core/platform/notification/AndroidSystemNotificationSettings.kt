package com.hopcape.odo.core.platform.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Reads the OS notification permission and opens the system page for it.
 *
 * `FLAG_ACTIVITY_NEW_TASK` because the Context here is the application's, not an
 * activity's. A device with no settings activity for this intent (rare, but possible on
 * heavily modified builds) leaves the owner where they are rather than crashing.
 */
internal class AndroidSystemNotificationSettings(
    private val context: Context,
) : SystemNotificationSettings {

    override fun areEnabled(): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false

    override fun open() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
