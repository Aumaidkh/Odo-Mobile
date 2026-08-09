package com.hopcape.odo.core.data.notification

import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler

/**
 * Accepts every refresh and schedules nothing — the stand-in on a platform with no scheduler
 * behind it.
 *
 * It keeps the shared graph complete: every write that changes a document asks for a refresh,
 * and a platform that cannot deliver notifications answers by doing nothing rather than by
 * failing to resolve. Android replaces this in `corePlatformAndroidModule`, which is listed
 * after this module, so the real scheduler wins.
 */
internal class NoopDocumentReminderScheduler : DocumentReminderScheduler {
    override suspend fun refresh() = Unit
}
