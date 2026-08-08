package com.hopcape.odo.core.triptracker.service

/** Notification action strings the live notification's `PendingIntent`s carry (M5). */
internal object TripTrackingActions {
    const val ACTION_PAUSE = "com.hopcape.odo.core.triptracker.action.PAUSE"
    const val ACTION_RESUME = "com.hopcape.odo.core.triptracker.action.RESUME"
    const val ACTION_DISCARD = "com.hopcape.odo.core.triptracker.action.DISCARD"
}

/** Which [com.hopcape.odo.core.triptracker.TripTracker] call a notification action maps to. */
internal enum class TripTrackerAction { PAUSE, RESUME, DISCARD }

/**
 * Pure — takes the intent's action string, not an `Intent`, so the mapping is unit
 * testable without instrumentation. `null` for anything that isn't a known action,
 * including the plain (no-action) intent [com.hopcape.odo.core.triptracker.port.TripForegroundSession]
 * uses to start the service.
 */
internal fun routeNotificationAction(action: String?): TripTrackerAction? = when (action) {
    TripTrackingActions.ACTION_PAUSE -> TripTrackerAction.PAUSE
    TripTrackingActions.ACTION_RESUME -> TripTrackerAction.RESUME
    TripTrackingActions.ACTION_DISCARD -> TripTrackerAction.DISCARD
    else -> null
}
