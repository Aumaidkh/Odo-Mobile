package com.hopcape.odo.core.platform.notification

/** Plans the evening drive summary. Best-effort; never throws. */
fun interface DriveSummaryNotifier {

    /** Plan the next daily and weekly runs, or cancel each when its setting or push is off. */
    suspend fun rearm()
}
