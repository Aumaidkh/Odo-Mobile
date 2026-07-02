package com.hopcape.logging.internal.sinks

import com.hopcape.logging.RecordingSink
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent
import com.hopcape.logging.internal.redactor.RegexPiiRedactor
import kotlin.test.Test
import kotlin.test.assertEquals

class RedactingSinkTest {

    private fun event(fields: Map<String, Any?>): LogEvent =
        LogEvent.Builder("T", "e").level(LogLevel.INFO).fields(fields).build()

    @Test
    fun redactsBeforeDelegatingToTheWrappedSink() {
        val downstream = RecordingSink()
        val sink = RedactingSink(downstream, RegexPiiRedactor())

        sink.write(event(mapOf("email" to "a@b.com", "n" to 7)))

        val delivered = downstream.written.single()
        assertEquals("***email_masked***", delivered.fields["email"])
        assertEquals(7, delivered.fields["n"])
    }

    @Test
    fun flush_isForwardedToDelegate() {
        val downstream = RecordingSink()
        RedactingSink(downstream, RegexPiiRedactor()).flush()
        assertEquals(1, downstream.flushCount)
    }
}
