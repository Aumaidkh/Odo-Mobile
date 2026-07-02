package com.hopcape.logging.internal.sinks

import com.hopcape.logging.RecordingSink
import com.hopcape.logging.ThrowingSink
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SafeSinkTest {

    private val event = LogEvent.Builder("T", "e").level(LogLevel.INFO).build()

    @Test
    fun write_swallowsExceptions_andReportsThem() {
        val boom = RuntimeException("disk full")
        val captured = mutableListOf<Throwable>()

        // Must not throw:
        SafeSink(ThrowingSink(boom)) { captured += it }.write(event)

        assertEquals(1, captured.size)
        assertSame(boom, captured.single())
    }

    @Test
    fun flush_swallowsExceptions_andReportsThem() {
        val captured = mutableListOf<Throwable>()
        SafeSink(ThrowingSink()) { captured += it }.flush()
        assertEquals(1, captured.size)
    }

    @Test
    fun healthySink_isDelegatedTo_withoutReportingErrors() {
        val downstream = RecordingSink()
        val captured = mutableListOf<Throwable>()
        val sink = SafeSink(downstream) { captured += it }

        sink.write(event)
        sink.flush()

        assertEquals(1, downstream.written.size)
        assertEquals(1, downstream.flushCount)
        assertTrue(captured.isEmpty())
    }
}
