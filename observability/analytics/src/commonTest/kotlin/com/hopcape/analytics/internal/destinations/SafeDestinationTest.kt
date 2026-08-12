package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.RecordingDestination
import com.hopcape.analytics.testEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeDestinationTest {

    @Test
    fun track_swallowsExceptions_andReportsThem() {
        val captured = mutableListOf<Pair<String, Throwable>>()
        val safe = SafeDestination(RecordingDestination(name = "boom", throwTimes = 1)) { name, error ->
            captured += name to error
        }

        // Must not throw, and a throw counts as a failed delivery:
        val delivered = safe.track(testEvent("e"))

        assertEquals(false, delivered)
        assertEquals(1, captured.size)
        assertEquals("boom", captured.single().first)
    }

    @Test
    fun healthyDestination_isDelegatedTo_withoutReportingErrors() {
        val downstream = RecordingDestination()
        val captured = mutableListOf<Throwable>()
        val safe = SafeDestination(downstream) { _, e -> captured += e }

        val delivered = safe.track(testEvent("e"))
        safe.flush()

        assertEquals(true, delivered)
        assertEquals(1, downstream.tracked.size)
        assertEquals(1, downstream.flushCount)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun name_isDelegated() {
        assertEquals("recording", SafeDestination(RecordingDestination()) { _, _ -> }.name)
    }
}
