package com.hopcape.performance

import com.hopcape.performance.internal.export.SpanExporter
import com.hopcape.performance.internal.model.CompletedSpan
import com.hopcape.performance.internal.model.SpanContext
import com.hopcape.performance.internal.store.SpanStore

/**
 * Test doubles + builders shared across the performance suite. They implement the
 * module's (internal) [SpanExporter] and [SpanStore] ports so the real pipeline
 * can be exercised without a live APM backend — the point of the ports.
 */

/** A fixed context for building spans in tests. */
internal fun testContext(): SpanContext = SpanContext(
    appVersion = "1.0.0",
    deviceModel = "Pixel-Test",
    osVersion = "Android 14",
    locale = "en-IN",
)

/** Builds a minimal [CompletedSpan] with a stable, unique id per call. */
internal fun testSpan(
    name: String,
    spanId: String,
    traceId: String = "trace-test",
    parentSpanId: String? = null,
    attributes: Map<String, Any?> = emptyMap(),
    durationMs: Long = 100L,
    sequenceNumber: Long = 0L,
): CompletedSpan = CompletedSpan(
    name = name,
    traceId = traceId,
    spanId = spanId,
    parentSpanId = parentSpanId,
    attributes = attributes,
    startEpochMs = 0L,
    durationMs = durationMs,
    context = testContext(),
    sequenceNumber = sequenceNumber,
)

/** A [SpanStore] that records operations in a plain list — deterministic for assertions. */
internal class RecordingSpanStore : SpanStore {
    val spans = mutableListOf<CompletedSpan>()

    override fun enqueue(span: CompletedSpan) {
        spans += span
    }

    override fun peekBatch(maxSize: Int): List<CompletedSpan> = spans.take(maxSize)

    override fun remove(spanIds: List<String>) {
        val ids = spanIds.toHashSet()
        spans.removeAll { it.spanId in ids }
    }

    override fun size(): Int = spans.size
}

/**
 * A [SpanExporter] that records everything it receives. [failTimes] makes the first
 * N `export` calls throw, so retry/dead-letter paths can be exercised.
 */
internal class RecordingSpanExporter(
    override val name: String = "recording",
    private var failTimes: Int = 0,
) : SpanExporter {

    val exported = mutableListOf<CompletedSpan>()
    var flushCount = 0
        private set

    override fun export(span: CompletedSpan) {
        if (failTimes > 0) {
            failTimes--
            throw RuntimeException("exporter boom")
        }
        exported += span
    }

    override fun flush() {
        flushCount++
    }
}

/** A [Sampler]-free helper: keep-all / drop-all samplers for deterministic tracer tests. */
internal object Samplers {
    val KEEP_ALL = object : com.hopcape.performance.internal.sampling.Sampler {
        override fun shouldSample(span: CompletedSpan): Boolean = true
    }
    val DROP_ALL = object : com.hopcape.performance.internal.sampling.Sampler {
        override fun shouldSample(span: CompletedSpan): Boolean = false
    }
}
