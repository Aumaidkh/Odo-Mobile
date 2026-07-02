@file:OptIn(ExperimentalUuidApi::class)

package com.hopcape.performance.internal

import com.hopcape.performance.api.Span
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ─────────────────────────────────────────────────────────────
// RecordingSpan — the real, mutable [Span] handed to callers by the
// tracer. It captures the start instant (wall clock, for the
// dashboard timestamp) and a monotonic mark (for a duration immune
// to wall-clock jumps / NTP corrections), and accumulates attributes
// until the tracer closes it.
//
// Concurrency: a span is expected to be decorated by a single flow
// (the coroutine/thread that owns the operation), so attributes use
// a plain map. The [ended] flag is atomic so a double endSpan() —
// e.g. a `finally` plus an explicit end — records the span once.
// Internal: callers only ever see the [Span] interface.
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalAtomicApi::class)
internal class RecordingSpan(
    override val name: String,
    override val traceId: String,
    override val parentSpanId: String?,
    val startEpochMs: Long,
    val startMark: TimeSource.Monotonic.ValueTimeMark,
    override val spanId: String = Uuid.random().toString(),
) : Span {

    private val attributes = LinkedHashMap<String, Any?>()
    private val ended = AtomicBoolean(false)

    override fun setAttribute(key: String, value: Any?): Span {
        attributes[key] = value
        return this
    }

    /** Snapshot of attributes for the immutable [com.hopcape.performance.internal.model.CompletedSpan]. */
    fun attributesSnapshot(): Map<String, Any?> = attributes.toMap()

    /**
     * Transitions the span to ended exactly once. Returns true for the first
     * caller (which should record it) and false for every subsequent call, so a
     * double end is a silent no-op rather than a duplicate span.
     */
    fun markEnded(): Boolean = ended.compareAndSet(false, true)
}
