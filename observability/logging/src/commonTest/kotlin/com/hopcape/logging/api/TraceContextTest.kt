package com.hopcape.logging.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TraceContextTest {

    @Test
    fun defaults_areAllNull() {
        val tc = TraceContext()
        assertNull(tc.sessionId)
        assertNull(tc.flowId)
        assertNull(tc.traceId)
    }

    @Test
    fun withNewTrace_setsTraceId_keepsSessionAndFlow() {
        val tc = TraceContext(sessionId = "s1", flowId = "f1", traceId = "old")

        val child = tc.withNewTrace("t2")

        assertEquals("s1", child.sessionId)
        assertEquals("f1", child.flowId)
        assertEquals("t2", child.traceId)
    }

    @Test
    fun withNewFlow_setsFlow_andResetsTraceByDefault() {
        val tc = TraceContext(sessionId = "s1", flowId = "old", traceId = "old")

        val child = tc.withNewFlow("f2")

        assertEquals("s1", child.sessionId)
        assertEquals("f2", child.flowId)
        assertNull(child.traceId, "flow change resets trace unless one is supplied")
    }

    @Test
    fun withNewFlow_canSetTraceExplicitly() {
        val child = TraceContext(sessionId = "s1").withNewFlow("f2", traceId = "t2")

        assertEquals("f2", child.flowId)
        assertEquals("t2", child.traceId)
    }

    @Test
    fun copy_doesNotMutateOriginal() {
        val original = TraceContext(sessionId = "s1")
        original.withNewTrace("t2")
        assertNull(original.traceId, "TraceContext is immutable")
    }
}
