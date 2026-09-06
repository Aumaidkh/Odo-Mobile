package com.hopcape.odo

import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.navigation.OdoDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Coverage for the app-shell's D4 redirect guard (docs/AUTO_ODOMETER_PLAN.md §4.4). */
class PendingTripRedirectTest {

    private val tripId = TripId("trip-1")

    /**
     * Both flag states run in this one suite. The flag used to be a `const`, so these tests
     * could not set it and guarded themselves with an early return instead — only whichever
     * half the build was compiled for ever ran.
     */
    private fun config(autoOdometer: Boolean = true) = object : FeatureConfig {
        override val autoOdometerEnabled = autoOdometer
        override val refuelDetectEnabled = true
        override val challanEnabled = false
        override val plateLookupEnabled = false
        override val advisoryClassifierEnabled = false
        override val billCheckEnabled = false
        override val serviceChecklistEnabled = false
    }

    @Test
    fun noPendingTrip_neverRedirects() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Home, pendingTripId = null, config()))
    }

    @Test
    fun pendingTrip_onATopLevelTab_redirects() {
        assertTrue(shouldRedirectToTripLogged(OdoDestination.Home, tripId, config()))
        assertTrue(shouldRedirectToTripLogged(OdoDestination.Garage.Home, tripId, config()))
    }

    /** The twin: the one case that would redirect stays put while the feature is off. */
    @Test
    fun whileTheFeatureIsOff_evenATopLevelTabNeverRedirects() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Home, tripId, config(autoOdometer = false)))
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Garage.Home, tripId, config(autoOdometer = false)))
    }

    @Test
    fun pendingTrip_onTheTripLoggedScreenItself_doesNotReRedirect() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.AutoOdometer.TripLogged(tripId.value), tripId, config()))
    }

    @Test
    fun pendingTrip_midFlowOnANestedScreen_doesNotInterrupt() {
        assertFalse(shouldRedirectToTripLogged(OdoDestination.Garage.AddCar, tripId, config()))
        assertFalse(shouldRedirectToTripLogged(OdoDestination.ServiceLog.AddEdit(carId = "car-1"), tripId, config()))
    }

    @Test
    fun pendingTrip_noCurrentDestinationYet_doesNotRedirect() {
        assertFalse(shouldRedirectToTripLogged(currentDestination = null, tripId, config()))
    }
}
