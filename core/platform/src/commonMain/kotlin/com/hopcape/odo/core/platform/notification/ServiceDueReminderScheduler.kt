package com.hopcape.odo.core.platform.notification

/** Plans the service-due notifications for the active car. Best-effort; never throws. */
fun interface ServiceDueReminderScheduler {

    /** Rebuild the schedule from the service log and the owner's settings. */
    suspend fun refresh()
}
