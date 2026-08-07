package com.hopcape.odo.core.triptracker.algorithm

import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.MotionSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MotionDebouncerTest {

    private val config = TripTrackerConfig(motionDebounceConsecutiveReadings = 3, motionDebounceMinConfidence = 75)
    private val debouncer = MotionDebouncer(config)

    @Test
    fun settlesOnlyAfterNConsecutiveGoodReadings() {
        assertNull(debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
        assertNull(debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
        assertEquals(MotionKind.IN_VEHICLE, debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
    }

    @Test
    fun singleLowConfidenceReading_doesNotBreakTheStreak() {
        assertNull(debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
        assertNull(debouncer.accept(signal(MotionKind.WALKING, 40))) // below threshold, ignored outright
        assertNull(debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
        assertEquals(MotionKind.IN_VEHICLE, debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
    }

    @Test
    fun confidentContraryReading_resetsTheStreak() {
        assertNull(debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
        assertNull(debouncer.accept(signal(MotionKind.IN_VEHICLE, 90)))
        assertNull(debouncer.accept(signal(MotionKind.WALKING, 90))) // confident and different: real evidence
        assertNull(debouncer.accept(signal(MotionKind.WALKING, 90)))
        assertEquals(MotionKind.WALKING, debouncer.accept(signal(MotionKind.WALKING, 90)))
    }

    @Test
    fun reachingAnAlreadySettledKind_doesNotResignal() {
        repeat(3) { debouncer.accept(signal(MotionKind.STILL, 90)) }
        assertEquals(MotionKind.STILL, debouncer.current())
        assertNull(debouncer.accept(signal(MotionKind.STILL, 90)))
    }

    private fun signal(kind: MotionKind, confidence: Int) =
        MotionSignal(kind = kind, confidence = confidence, at = Instant.fromEpochSeconds(0))
}
