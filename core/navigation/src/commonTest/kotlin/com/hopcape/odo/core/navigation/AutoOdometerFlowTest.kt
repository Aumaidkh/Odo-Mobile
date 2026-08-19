package com.hopcape.odo.core.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which destinations come off the stack when setup ends.
 *
 * The set exists because finishing used to push the garage on top of the whole run: pressing
 * back from it walked the owner into the permission rationales again.
 */
class AutoOdometerFlowTest {

    @Test
    fun everyPageOfTheSetupRun_isAStep() {
        val steps = listOf(
            OdoDestination.AutoOdometer.Education(),
            OdoDestination.AutoOdometer.Education(OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO),
            OdoDestination.AutoOdometer.NotificationRationale(),
            OdoDestination.AutoOdometer.DevicePicker,
            OdoDestination.AutoOdometer.PermissionSetup(),
        )

        steps.forEach { assertTrue(isAutoOdometerFlowStep(it), "$it should be a setup step") }
    }

    /** Reached from a notification long after setup — popping it as part of the run would strand the owner. */
    @Test
    fun tripLogged_isNotAStep() {
        assertFalse(isAutoOdometerFlowStep(OdoDestination.AutoOdometer.TripLogged(tripId = "trip-1")))
    }

    /** Reached from the garage, on its own, by an owner who set this up weeks ago. */
    @Test
    fun settings_isNotAStep() {
        assertFalse(isAutoOdometerFlowStep(OdoDestination.AutoOdometer.Settings))
    }

    @Test
    fun destinationsOfOtherFeatures_areNotSteps() {
        assertFalse(isAutoOdometerFlowStep(OdoDestination.Garage.Home))
    }
}
