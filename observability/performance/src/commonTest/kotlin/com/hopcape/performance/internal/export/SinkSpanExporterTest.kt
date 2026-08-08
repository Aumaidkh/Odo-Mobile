package com.hopcape.performance.internal.export

import com.hopcape.performance.api.SpanSink
import com.hopcape.performance.testSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SinkSpanExporterTest {

    private class RecordingSink(
        override val name: String = "vendor",
        private val accept: Boolean = true,
    ) : SpanSink {
        var lastAttributes: Map<String, String>? = null
        var lastIsError: Boolean? = null
        var flushCount = 0

        override fun export(
            name: String,
            traceId: String,
            spanId: String,
            parentSpanId: String?,
            startEpochMs: Long,
            durationMs: Long,
            isError: Boolean,
            attributes: Map<String, String>,
        ): Boolean {
            lastAttributes = attributes
            lastIsError = isError
            return accept
        }

        override fun flush() {
            flushCount++
        }
    }

    @Test
    fun export_forwardsResolvedValues_toTheSink() {
        val sink = RecordingSink()
        val exporter = SinkSpanExporter(sink)

        exporter.export(testSpan("checkout", "1", attributes = mapOf("http_status" to 200)))

        assertEquals("200", sink.lastAttributes?.get("http_status"))
        assertEquals(false, sink.lastIsError)
    }

    @Test
    fun export_marksErrorSpans() {
        val sink = RecordingSink()
        val exporter = SinkSpanExporter(sink)

        exporter.export(testSpan("checkout", "1", attributes = mapOf("error" to "OUT_OF_STOCK")))

        assertEquals(true, sink.lastIsError)
    }

    @Test
    fun export_redactsPiiInStringAttributeValues() {
        val sink = RecordingSink()
        val exporter = SinkSpanExporter(sink)

        exporter.export(testSpan("checkout", "1", attributes = mapOf("contact" to "user@example.com")))

        assertTrue(sink.lastAttributes?.get("contact")?.contains("masked") == true)
    }

    @Test
    fun export_whenSinkRejects_throwsSoDispatcherRetries() {
        val exporter = SinkSpanExporter(RecordingSink(accept = false))

        assertFailsWith<IllegalStateException> { exporter.export(testSpan("checkout", "1")) }
    }

    @Test
    fun export_whenSinkAccepts_doesNotThrow() {
        val exporter = SinkSpanExporter(RecordingSink(accept = true))

        exporter.export(testSpan("checkout", "1")) // must not throw
    }

    @Test
    fun name_forwardsFromSink() {
        assertEquals("vendor", SinkSpanExporter(RecordingSink(name = "vendor")).name)
    }

    @Test
    fun flush_forwardsToSink() {
        val sink = RecordingSink()
        SinkSpanExporter(sink).flush()

        assertEquals(1, sink.flushCount)
    }
}
