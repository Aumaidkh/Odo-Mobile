package com.hopcape.performance.internal

import com.hopcape.performance.api.PerformanceConfig
import com.hopcape.performance.api.SpanSink
import com.hopcape.performance.internal.model.SpanContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PerformanceFactoryTest {

    private class RecordingSink : SpanSink {
        override val name: String = "vendor"
        val received = mutableListOf<String>()

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
            received += name
            return true
        }

        override fun flush() {}
    }

    // The factory wires the dispatcher onto its own Dispatchers.Default scope (not
    // injectable here), so delivery is genuinely asynchronous — poll instead of
    // asserting immediately after flush().
    @Test
    fun create_registeredSink_receivesEndedSpans() = runBlocking {
        val sink = RecordingSink()
        val tracer = PerformanceFactory.create(
            config = PerformanceConfig(
                appVersion = "1.0.0",
                deviceModel = "Pixel-Test",
                osVersion = "Android 14",
                locale = "en-IN",
                isDebug = true,
                destinations = listOf(sink),
            ),
            contextProvider = { SpanContext(appVersion = "1.0.0", deviceModel = "Pixel-Test", osVersion = "Android 14", locale = "en-IN") },
        )

        val span = tracer.startSpan(name = "checkout", traceId = "trace-1")
        tracer.endSpan(span)
        tracer.flush()

        withTimeout(2.seconds) {
            while (sink.received.isEmpty()) delay(10)
        }
        assertEquals(listOf("checkout"), sink.received)
    }
}
