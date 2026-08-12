package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TripValidatorTest {

    private val config = TripTrackerConfig()
    private val validator = TripValidator(config)

    @Test
    fun overOneKm_recordsRegardlessOfDuration() {
        val verdict = validator.validate(
            distanceMeters = 1_200,
            estimatedMeters = 0,
            duration = 10.seconds,
            attributionMarginal = false,
        )
        assertEquals(Verdict(save = true, status = TripStatus.RECORDED), verdict)
    }

    @Test
    fun midBucket_withEnoughDuration_records() {
        val verdict = validator.validate(
            distanceMeters = 300,
            estimatedMeters = 0,
            duration = 3.minutes,
            attributionMarginal = false,
        )
        assertEquals(Verdict(save = true, status = TripStatus.RECORDED), verdict)
    }

    @Test
    fun midBucket_withoutEnoughDuration_needsConfirmation() {
        val verdict = validator.validate(
            distanceMeters = 300,
            estimatedMeters = 0,
            duration = 1.minutes,
            attributionMarginal = false,
        )
        assertEquals(Verdict(save = true, status = TripStatus.NEEDS_CONFIRMATION), verdict)
    }

    @Test
    fun smallBucket_needsConfirmation() {
        val verdict = validator.validate(
            distanceMeters = 150,
            estimatedMeters = 0,
            duration = 30.seconds,
            attributionMarginal = false,
        )
        assertEquals(Verdict(save = true, status = TripStatus.NEEDS_CONFIRMATION), verdict)
    }

    @Test
    fun belowFloor_discarded() {
        val verdict = validator.validate(
            distanceMeters = 90,
            estimatedMeters = 0,
            duration = 10.seconds,
            attributionMarginal = false,
        )
        assertEquals(Verdict(save = false, status = null), verdict)
    }

    @Test
    fun heavyEstimatedPortion_downgradesAnOtherwiseRecordedTrip() {
        val verdict = validator.validate(
            distanceMeters = 1_200,
            estimatedMeters = 600,
            duration = 2.minutes,
            attributionMarginal = false,
        )
        assertEquals(Verdict(save = true, status = TripStatus.NEEDS_CONFIRMATION), verdict)
    }

    @Test
    fun marginalAttribution_downgradesAnOtherwiseRecordedTrip() {
        val verdict = validator.validate(
            distanceMeters = 1_200,
            estimatedMeters = 0,
            duration = 2.minutes,
            attributionMarginal = true,
        )
        assertEquals(Verdict(save = true, status = TripStatus.NEEDS_CONFIRMATION), verdict)
    }
}
