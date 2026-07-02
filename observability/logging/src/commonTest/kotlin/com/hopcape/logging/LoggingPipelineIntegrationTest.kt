package com.hopcape.logging

import com.hopcape.logging.api.Logger
import com.hopcape.logging.internal.LoggerImpl
import com.hopcape.logging.internal.redactor.RegexPiiRedactor
import com.hopcape.logging.internal.sinks.RedactingSink
import com.hopcape.logging.internal.sinks.SafeSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test: assembles the REAL internal pipeline exactly as
 * `LoggerFactory.create` does — `LoggerImpl` → `SafeSink(RedactingSink(sink))` —
 * but with a recording leaf sink instead of Logcat/File, so the end-to-end
 * behaviour (redaction + failure isolation + fan-out) is observable and asserted.
 */
class LoggingPipelineIntegrationTest {

    private val capturedErrors = mutableListOf<Throwable>()

    private fun pipeline(vararg leaves: com.hopcape.logging.internal.sinks.LogSink): Logger =
        LoggerImpl(
            leaves.map { leaf ->
                SafeSink(RedactingSink(leaf, RegexPiiRedactor())) { capturedErrors += it }
            },
        )

    @Test
    fun redactsPiiFieldValues_endToEnd() {
        val leaf = RecordingSink()

        pipeline(leaf).info(
            tag = "Auth",
            event = "login ok",
            fields = mapOf("email" to "user@odo.app", "phone" to "9876543210", "city" to "Pune"),
        )

        val e = leaf.written.single()
        assertEquals("***email_masked***", e.fields["email"])
        assertEquals("***phone_masked***", e.fields["phone"])
        assertEquals("Pune", e.fields["city"])
        assertTrue(capturedErrors.isEmpty())
    }

    @Test
    fun oneFailingSink_doesNotStopOthers_norThrow() {
        val healthy = RecordingSink()

        // A throwing sink alongside a healthy one — SafeSink must isolate the failure.
        pipeline(ThrowingSink(), healthy).warn("T", "still delivered")

        assertEquals(1, healthy.written.size, "healthy sink must still receive the event")
        assertEquals(1, capturedErrors.size, "the throwing sink's failure must be reported, not propagated")
    }

    @Test
    fun fansOutToEverySink() {
        val a = RecordingSink()
        val b = RecordingSink()

        pipeline(a, b).error("T", "boom")

        assertEquals(1, a.written.size)
        assertEquals(1, b.written.size)
    }

    @Test
    fun flushPropagatesThroughAllLayers() {
        val leaf = RecordingSink()
        pipeline(leaf).flush()
        assertEquals(1, leaf.flushCount)
    }
}
