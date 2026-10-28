package com.hopcape.odo.core.platform.notification

/** Plans the evening drive summary. Best-effort; never throws. */
fun interface DriveSummaryNotifier {

    /** Plan the next 21:00 run, or cancel it when the setting or push is off. */
    suspend fun rearm()
}
