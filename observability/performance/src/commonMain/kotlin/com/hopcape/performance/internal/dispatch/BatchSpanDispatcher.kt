@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.performance.internal.dispatch

import com.hopcape.performance.internal.export.SpanExporter
import com.hopcape.performance.internal.model.CompletedSpan
import com.hopcape.performance.internal.store.SpanStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// ─────────────────────────────────────────────────────────────
// BatchSpanDispatcher — the engine room. It pulls sampled spans from
// the SpanStore, batches them (size OR time triggered), sends them to
// every exporter, retries on failure, and only removes a span from
// the store once delivery is confirmed (or it is dead-lettered after
// exhausting retries). Structurally identical to the analytics
// BatchDispatcher — same KMP-native design (coroutine flush loop on
// Dispatchers.Default, a coroutine Mutex serializing the critical
// section) so the retry bookkeeping needs no concurrent collection.
// ─────────────────────────────────────────────────────────────
internal class BatchSpanDispatcher(
    private val store: SpanStore,
    private val exporters: List<SpanExporter>,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushInterval: Duration = DEFAULT_FLUSH_INTERVAL,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onDropped: (CompletedSpan, Throwable) -> Unit = { _, _ -> },
) {
    private val sequenceCounter = AtomicLong(0)

    // Retry counts per span id. Only ever touched inside [dispatchMutex],
    // so a plain map is safe — no concurrent collection required.
    private val attemptCounts = mutableMapOf<String, Int>()
    private val dispatchMutex = Mutex()
    private var timerJob: Job? = null

    /** Starts the periodic time-triggered flush loop. Idempotent. */
    fun start() {
        if (timerJob != null) return
        timerJob = scope.launch {
            while (isActive) {
                delay(flushInterval)
                dispatchOnce()
            }
        }
    }

    /** Monotonic sequence numbers for ordering — safe to call from the caller thread. */
    fun nextSequenceNumber(): Long = sequenceCounter.addAndFetch(1L)

    /** Size trigger — kicks a dispatch as soon as the store reaches [batchSize]. */
    fun dispatchIfBatchFull() {
        if (store.size() >= batchSize) scope.launch { dispatchOnce() }
    }

    /** Explicit flush() — schedule a drain and ask exporters to persist. */
    fun flushNow() {
        scope.launch { dispatchOnce() }
        exporters.forEach { it.flush() }
    }

    private suspend fun dispatchOnce() = dispatchMutex.withLock {
        val batch = store.peekBatch(batchSize)
        if (batch.isEmpty()) return@withLock

        val settledIds = mutableListOf<String>()
        for (span in batch) {
            val allDelivered = exporters.all { exporter ->
                runCatching { exporter.export(span) }.isSuccess
            }

            when {
                allDelivered -> {
                    settledIds += span.spanId
                    attemptCounts.remove(span.spanId)
                }

                else -> {
                    val nextAttempt = (attemptCounts[span.spanId] ?: 0) + 1
                    if (retryPolicy.shouldGiveUp(nextAttempt)) {
                        // Dead-letter: surface it and remove so it can't wedge the queue.
                        onDropped(span, IllegalStateException("Max retry attempts ($nextAttempt) exceeded"))
                        settledIds += span.spanId
                        attemptCounts.remove(span.spanId)
                    } else {
                        // Left in the store; retried on the next dispatch cycle.
                        attemptCounts[span.spanId] = nextAttempt
                    }
                }
            }
        }

        if (settledIds.isNotEmpty()) store.remove(settledIds)
    }

    /** Stops the flush loop and tears down the dispatcher's coroutine scope. */
    fun shutdown() {
        timerJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 20
        val DEFAULT_FLUSH_INTERVAL: Duration = 15.seconds
    }
}
