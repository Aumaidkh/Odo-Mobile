package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.RecordingCrashDestination
import com.hopcape.crashreporting.testReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeCrashDestinationTest {

    @Test
    fun record_swallowsExceptions_andReportsThem() {
        val captured = mutableListOf<Pair<String, Throwable>>()
        val safe = SafeCrashDestination(RecordingCrashDestination(name = "boom", failRecordTimes = 1)) { name, error ->
            captured += name to error
        }

        // Must not throw — critical, since we're often already handling a crash.
        safe.record(testReport())

        assertEquals(1, captured.size)
        assertEquals("boom", captured.single().first)
    }

    @Test
    fun healthyDestination_isDelegatedTo_withoutReportingErrors() {
        val downstream = RecordingCrashDestination()
        val captured = mutableListOf<Throwable>()
        val safe = SafeCrashDestination(downstream) { _, e -> captured += e }

        safe.record(testReport())
        safe.setCustomKey("k", "v")
        safe.setUserId("u-1")

        assertEquals(1, downstream.recorded.size)
        assertEquals("k" to "v", downstream.customKeys.single())
        assertEquals("u-1", downstream.userId)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun name_isDelegated() {
        assertEquals("recording", SafeCrashDestination(RecordingCrashDestination()) { _, _ -> }.name)
    }
}
