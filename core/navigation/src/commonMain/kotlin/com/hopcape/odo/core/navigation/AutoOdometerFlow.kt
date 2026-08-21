package com.hopcape.odo.core.navigation

/**
 * True when [destination] is a step of setting auto-odometer up.
 *
 * The errand runs from the explainer through the notification case, the device picker and the
 * permission steps. Every one of those is a page the owner is walked through once; none of them
 * is a place to come back to. Pass this to [NavigationCommand.FinishFlow] when setup ends, so
 * the whole run comes off the stack at once and pressing back from where they land cannot walk
 * them into a rationale for a permission they have already answered.
 *
 * [OdoDestination.AutoOdometer.TripLogged] and [OdoDestination.AutoOdometer.Settings] are
 * deliberately not steps. They are destinations of their own that happen to belong to the same
 * feature — one is reached from a notification long after setup, the other from the garage —
 * and popping them as part of an errand they were never part of would strand the owner.
 */
fun isAutoOdometerFlowStep(destination: OdoDestination): Boolean = when (destination) {
    is OdoDestination.AutoOdometer.Education,
    is OdoDestination.AutoOdometer.NotificationRationale,
    is OdoDestination.AutoOdometer.DevicePicker,
    is OdoDestination.AutoOdometer.PermissionSetup,
    -> true

    else -> false
}
