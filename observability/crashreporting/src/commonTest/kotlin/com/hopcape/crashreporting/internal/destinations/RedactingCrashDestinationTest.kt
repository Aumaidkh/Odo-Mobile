package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.RecordingCrashDestination
import com.hopcape.crashreporting.testReport
import com.hopcape.crashreporting.internal.redactor.RegexCrashPiiRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactingCrashDestinationTest {

    @Test
    fun scrubsReportBeforeDelegating() {
        val downstream = RecordingCrashDestination()
        val redacting = RedactingCrashDestination(downstream, RegexCrashPiiRedactor())

        redacting.record(testReport(throwableMessage = "token for a@b.com"))

        val delivered = downstream.recorded.single()
        assertTrue(delivered.throwableMessage!!.contains("***email_masked***"))
        assertFalse(delivered.throwableMessage.contains("a@b.com"))
    }

    @Test
    fun forwardsIdentityOperationsUntouched() {
        val downstream = RecordingCrashDestination()
        val redacting = RedactingCrashDestination(downstream, RegexCrashPiiRedactor())

        redacting.setCustomKey("screen", "home")
        redacting.setUserId("u-42")

        assertEquals("screen" to "home", downstream.customKeys.single())
        assertEquals("u-42", downstream.userId)
    }
}
