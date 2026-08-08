package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TripAttributionTest {

    private val config = TripTrackerConfig()
    private val attribution = TripAttribution(config)
    private val parkedPoint = GeoPoint.of(19.0760, 72.8777).getOrNull()!!
    private val parked = ParkedLocation(CarId("car-1"), parkedPoint, Instant.fromEpochSeconds(0))

    @Test
    fun btVerified_alwaysAllowedAndConfident_evenFarFromParked() {
        val farAway = GeoPoint.of(20.0, 74.0).getOrNull()!!
        val result = attribution.evaluate(TripMode.BT_VERIFIED, farAway, parked)
        assertEquals(AttributionResult(allowed = true, confident = true), result)
    }

    @Test
    fun gpsOnly_withinRadius_allowedAndConfident() {
        val nearby = GeoPoint.of(19.0761, 72.8778).getOrNull()!!
        val result = attribution.evaluate(TripMode.GPS_ONLY, nearby, parked)
        assertEquals(AttributionResult(allowed = true, confident = true), result)
    }

    @Test
    fun gpsOnly_outsideRadius_notAllowed() {
        val farAway = GeoPoint.of(19.20, 73.00).getOrNull()!!
        val result = attribution.evaluate(TripMode.GPS_ONLY, farAway, parked)
        assertEquals(AttributionResult(allowed = false, confident = false), result)
    }

    @Test
    fun gpsOnly_noParkedLocationYet_allowedButNotConfident() {
        val anywhere = GeoPoint.of(19.20, 73.00).getOrNull()!!
        val result = attribution.evaluate(TripMode.GPS_ONLY, anywhere, parked = null)
        assertEquals(AttributionResult(allowed = true, confident = false), result)
    }
}
