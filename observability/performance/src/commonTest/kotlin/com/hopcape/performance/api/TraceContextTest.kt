package com.hopcape.performance.api

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
    fun withNewTrace_doesNotMutateOriginal() {
        val original = TraceContext(sessionId = "s1")
        original.withNewTrace("t2")
        assertNull(original.traceId, "TraceContext is immutable")
    }

    @Test
    fun valueEquality_holds_forCoroutineElement() {
        assertEquals(
            TraceContext(sessionId = "s1", flowId = "f1", traceId = "t1"),
            TraceContext(sessionId = "s1", flowId = "f1", traceId = "t1"),
        )
    }

    @Test
    fun propagates_downTheCoroutineTree_andIsReadableByCurrentTraceContext() = runTest {
        val installed = TraceContext(sessionId = "s1", flowId = "flow-shopping", traceId = "trace-1")
        var seen: TraceContext? = null

        launch(installed) {
            // A deep call site reads it back without it being passed as a parameter.
            seen = currentTraceContext()
        }.join()

        assertEquals(installed, seen)
    }

    @Test
    fun currentTraceContext_isEmpty_whenNoneInstalled() = runTest {
        assertEquals(TraceContext(), currentTraceContext())
    }
}
