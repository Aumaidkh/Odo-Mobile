@file:OptIn(ExperimentalUuidApi::class)

package com.hopcape.performance.internal

import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.internal.dispatch.BatchSpanDispatcher
import com.hopcape.performance.internal.model.CompletedSpan
import com.hopcape.performance.internal.model.SpanContext
import com.hopcape.performance.internal.sampling.Sampler
import com.hopcape.performance.internal.store.SpanStore
import kotlinx.datetime.Clock
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi

// ─────────────────────────────────────────────────────────────
// PerformanceTracerImpl — orchestrates the span lifecycle:
//   startSpan → (caller decorates) → endSpan → sample → enqueue → dispatch.
// Each concern lives in its own collaborator (SRP); this class only
// sequences them and depends solely on abstractions (DIP). It is
// assembled once by PerformanceFactory.
// ─────────────────────────────────────────────────────────────
internal class PerformanceTracerImpl(
    private val store: SpanStore,
    private val sampler: Sampler,
    private val dispatcher: BatchSpanDispatcher,
    private val contextProvider: () -> SpanContext,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : PerformanceTracer {

    override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
        RecordingSpan(
            name = name,
            traceId = traceId,
            parentSpanId = parentSpanId,
            startEpochMs = nowEpochMs(),
            startMark = TimeSource.Monotonic.markNow(),
        )

    override fun endSpan(span: Span) {
        // Only spans this tracer minted carry timing/attributes. A no-op span
        // (created before init) or any foreign Span is silently ignored (LSP).
        val recording = span as? RecordingSpan ?: return

        // Close exactly once — a `finally { endSpan }` plus an explicit end records once.
        if (!recording.markEnded()) return

        val completed = CompletedSpan(
            name = recording.name,
            traceId = recording.traceId,
            spanId = recording.spanId,
            parentSpanId = recording.parentSpanId,
            attributes = recording.attributesSnapshot(),
            startEpochMs = recording.startEpochMs,
            durationMs = recording.startMark.elapsedNow().inWholeMilliseconds,
            context = contextProvider(),
            sequenceNumber = dispatcher.nextSequenceNumber(),
        )

        // Sampling gate — errors and slow spans always pass (see AdaptiveSampler);
        // the fast, successful majority is kept at the configured rate.
        if (!sampler.shouldSample(completed)) return

        // Enqueue, then let the dispatcher deliver — never call exporters directly
        // here; the queue guarantees offline durability and retry.
        store.enqueue(completed)
        dispatcher.dispatchIfBatchFull()
    }

    override fun flush() = dispatcher.flushNow()
}
