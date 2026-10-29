package com.hopcape.odo.core.common.notification

import android.content.Context
import android.content.Intent

/** The app's launch intent, tagged with the notification [kind] that opens it. */
fun Context.notificationLaunchIntent(kind: String): Intent? =
    packageManager.getLaunchIntentForPackage(packageName)?.putExtra(NotificationOpen.EXTRA_KIND, kind)
