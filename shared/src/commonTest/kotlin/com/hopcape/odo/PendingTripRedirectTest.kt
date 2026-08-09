package com.hopcape.odo

import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.navigation.OdoDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Coverage for the app-shell's D4 redirect guard (docs/AUTO_ODOMETER_PLAN.md §4.4). */
class PendingTripRedirectTest {

    private val tripId = TripId("trip-1")

    @Test
    fun noPendingTrip_neverRedirects() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Home, pendingTripId = null))
    }

    @Test
    fun pendingTrip_onATopLevelTab_redirects() {
        if (!FeatureFlags.AUTO_ODOMETER_ENABLED) return
        assertTrue(shouldRedirectToTripLogged(OdoDestination.Home, tripId))
        assertTrue(shouldRedirectToTripLogged(OdoDestination.Garage.Home, tripId))
    }

    /** The 1.0 twin: the one case that would redirect stays put while the feature is off. */
    @Test
    fun whileTheFeatureIsOff_evenATopLevelTabNeverRedirects() {
        if (FeatureFlags.AUTO_ODOMETER_ENABLED) return
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Home, tripId))
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Garage.Home, tripId))
    }

    @Test
    fun pendingTrip_onTheTripLoggedScreenItself_doesNotReRedirect() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.AutoOdometer.TripLogged(tripId.value), tripId))
    }

    @Test
    fun pendingTrip_midFlowOnANestedScreen_doesNotInterrupt() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Garage.AddCar, tripId))
        assertFalse(shouldRedirectToTripLogged(OdoDestination.ServiceLog.AddEdit(carId = "car-1"), tripId))
    }

    @Test
    fun pendingTrip_noCurrentDestinationYet_doesNotRedirect() {
        assertFalse(shouldRedirectToTripLogged(currentDestination = null, tripId))
    }
}
