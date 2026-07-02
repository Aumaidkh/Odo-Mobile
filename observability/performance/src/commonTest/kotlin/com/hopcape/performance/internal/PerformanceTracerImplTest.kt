package com.hopcape.performance.internal

import com.hopcape.performance.RecordingSpanStore
import com.hopcape.performance.Samplers
import com.hopcape.performance.api.Span
import com.hopcape.performance.internal.dispatch.BatchSpanDispatcher
import com.hopcape.performance.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PerformanceTracerImplTest {

    // A huge batchSize keeps dispatchIfBatchFull() from launching a coroutine, so
    // these tests exercise the synchronous start→end→sample→enqueue path directly.
    private fun tracer(
        store: RecordingSpanStore,
        sampler: com.hopcape.performance.internal.sampling.Sampler = Samplers.KEEP_ALL,
    ) = PerformanceTracerImpl(
        store = store,
        sampler = sampler,
        dispatcher = BatchSpanDispatcher(store = store, exporters = emptyList(), batchSize = 1_000),
        contextProvider = { testContext() },
        nowEpochMs = { 0L },
    )

    @Test
    fun startSpan_exposesIdentifiers_andSetAttributeChains() {
        val store = RecordingSpanStore()
        val span = tracer(store).startSpan("login_flow", traceId = "trace-1")

        assertEquals("login_flow", span.name)
        assertEquals("trace-1", span.traceId)
        assertNull(span.parentSpanId)
        assertTrue(span.spanId.isNotBlank())
        assertSame(span, span.setAttribute("k", "v"), "setAttribute must return the same span for chaining")
    }

    @Test
    fun endSpan_enqueuesOneCompletedSpan_withAttributes() {
        val store = RecordingSpanStore()
        val t = tracer(store)

        val span = t.startSpan("login_api_call", traceId = "trace-1")
            .setAttribute("http_status", 200)
        t.endSpan(span)

        assertEquals(1, store.size())
        val completed = store.spans.single()
        assertEquals("login_api_call", completed.name)
        assertEquals(200, completed.attributes["http_status"])
    }

    @Test
    fun endSpan_isIdempotent_recordsOnce() {
        val store = RecordingSpanStore()
        val t = tracer(store)

        val span = t.startSpan("op", traceId = "trace-1")
        t.endSpan(span)
        t.endSpan(span) // e.g. an explicit end plus a finally{} end

        assertEquals(1, store.size(), "a double end must record the span only once")
    }

    @Test
    fun nestedSpan_carriesParentSpanId_onSameTrace() {
        val store = RecordingSpanStore()
        val t = tracer(store)

        val parent = t.startSpan("checkout_flow", traceId = "trace-checkout")
        val child = t.startSpan("inventory_check_api", traceId = "trace-checkout", parentSpanId = parent.spanId)
        t.endSpan(child)
        t.endSpan(parent)

        val childSpan = store.spans.first { it.name == "inventory_check_api" }
        assertEquals(parent.spanId, childSpan.parentSpanId)
        assertEquals("trace-checkout", childSpan.traceId)
    }

    @Test
    fun sampledOutSpan_isNotEnqueued() {
        val store = RecordingSpanStore()
        val t = tracer(store, sampler = Samplers.DROP_ALL)

        t.endSpan(t.startSpan("op", traceId = "trace-1"))

        assertEquals(0, store.size(), "a dropped span must not reach the store")
    }

    @Test
    fun endSpan_ignoresForeignSpan() {
        val store = RecordingSpanStore()
        val t = tracer(store)

        val foreign = object : Span {
            override val spanId = "x"
            override val traceId = "t"
            override val parentSpanId: String? = null
            override val name = "foreign"
            override fun setAttribute(key: String, value: Any?) = this
        }
        t.endSpan(foreign)

        assertEquals(0, store.size(), "a span not minted by this tracer must be ignored")
    }
}
