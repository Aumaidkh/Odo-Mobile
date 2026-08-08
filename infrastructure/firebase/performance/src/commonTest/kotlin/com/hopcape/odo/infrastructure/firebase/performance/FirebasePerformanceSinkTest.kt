package com.hopcape.odo.infrastructure.firebase.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirebasePerformanceSinkTest {

    private class FakeFirebaseTraceGateway(private val deliveryResult: Boolean = true) : FirebaseTraceGateway {
        val recorded = mutableListOf<Triple<String, Long, Map<String, String>>>()

        override fun record(traceName: String, durationMs: Long, attributes: Map<String, String>): Boolean {
            recorded += Triple(traceName, durationMs, attributes)
            return deliveryResult
        }
    }

    @Test
    fun export_sanitizesThenForwardsToTheGateway() {
        val gateway = FakeFirebaseTraceGateway()
        val sink = FirebasePerformanceSink(gateway = gateway)

        sink.export(
            name = "sync.run", traceId = "t1", spanId = "s1", parentSpanId = null,
            startEpochMs = 0L, durationMs = 620L, isError = false,
            attributes = mapOf("entity" to "trip"),
        )

        val (traceName, durationMs, attributes) = gateway.recorded.single()
        assertEquals("sync.run", traceName)
        assertEquals(620L, durationMs)
        assertEquals("trip", attributes["entity"])
    }

    @Test
    fun export_addsBuildTypeAndIsErrorAttributes() {
        val gateway = FakeFirebaseTraceGateway()
        val sink = FirebasePerformanceSink(gateway = gateway, buildType = "debug")

        sink.export(
            name = "sync.run", traceId = "t1", spanId = "s1", parentSpanId = null,
            startEpochMs = 0L, durationMs = 1L, isError = true, attributes = emptyMap(),
        )

        val (_, _, attributes) = gateway.recorded.single()
        assertEquals("debug", attributes["build_type"])
        assertEquals("true", attributes["is_error"])
    }

    @Test
    fun export_returnsTheGatewaysDeliveryResult() {
        val delivered = FirebasePerformanceSink(gateway = FakeFirebaseTraceGateway(deliveryResult = true))
            .export("op", "t1", "s1", null, 0L, 1L, false, emptyMap())
        val failed = FirebasePerformanceSink(gateway = FakeFirebaseTraceGateway(deliveryResult = false))
            .export("op", "t1", "s1", null, 0L, 1L, false, emptyMap())

        assertEquals(true, delivered)
        assertEquals(false, failed)
    }

    @Test
    fun export_withInvalidName_isDropped_gatewayNeverCalled() {
        val gateway = FakeFirebaseTraceGateway()
        val sink = FirebasePerformanceSink(gateway = gateway)

        sink.export("_reserved", "t1", "s1", null, 0L, 1L, false, emptyMap())

        assertTrue(gateway.recorded.isEmpty())
    }

    @Test
    fun export_withInvalidName_returnsTrue_soItIsNotRetriedForever() {
        val sink = FirebasePerformanceSink(gateway = FakeFirebaseTraceGateway())

        val delivered = sink.export("_reserved", "t1", "s1", null, 0L, 1L, false, emptyMap())

        assertEquals(true, delivered, "retrying an invalid name can never make it valid")
    }

    @Test
    fun name_isFirebase() {
        assertEquals("firebase", FirebasePerformanceSink(gateway = FakeFirebaseTraceGateway()).name)
    }

    @Test
    fun flush_doesNotThrow() {
        FirebasePerformanceSink(gateway = FakeFirebaseTraceGateway()).flush()
    }
}
