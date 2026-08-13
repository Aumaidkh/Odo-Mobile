package com.hopcape.odo.core.data.notification

import com.hopcape.odo.core.platform.notification.CustomReminderScheduler

/**
 * Accepts every refresh and schedules nothing — the stand-in for the owner's own reminders on
 * a platform with no scheduler behind it, and the twin of [NoopDocumentReminderScheduler].
 *
 * Android replaces this in `corePlatformAndroidModule`, which is listed after this module, so
 * the real scheduler wins there.
 */
internal class NoopCustomReminderScheduler : CustomReminderScheduler {
    override suspend fun refresh() = Unit
}
