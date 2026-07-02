@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.analytics.internal.dispatch

import com.hopcape.analytics.internal.destinations.AnalyticsDestination
import com.hopcape.analytics.internal.model.AnalyticsEvent
import com.hopcape.analytics.internal.store.EventStore
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
// BatchDispatcher — the engine room. It pulls events from the
// EventStore, batches them (size OR time triggered), sends them to
// every destination, retries on failure, and only removes an event
// from the store once delivery is confirmed (or it is dead-lettered
// after exhausting retries). This is what makes delivery
// "guaranteed" rather than the logger's "best effort".
//
// KMP-native: the periodic flush is a coroutine loop on
// Dispatchers.Default (not a JVM ScheduledExecutorService), and the
// critical section is serialized by a coroutine Mutex, so the retry
// bookkeeping needs no concurrent collection.
// ─────────────────────────────────────────────────────────────
internal class BatchDispatcher(
    private val store: EventStore,
    private val destinations: List<AnalyticsDestination>,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushInterval: Duration = DEFAULT_FLUSH_INTERVAL,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onDropped: (AnalyticsEvent, Throwable) -> Unit = { _, _ -> },
) {
    private val sequenceCounter = AtomicLong(0)

    // Retry counts per event id. Only ever touched inside [dispatchMutex],
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

    /** Explicit flush() — schedule a drain and ask destinations to persist. */
    fun flushNow() {
        scope.launch { dispatchOnce() }
        destinations.forEach { it.flush() }
    }

    private suspend fun dispatchOnce() = dispatchMutex.withLock {
        val batch = store.peekBatch(batchSize)
        if (batch.isEmpty()) return@withLock

        val settledIds = mutableListOf<String>()
        for (event in batch) {
            val allDelivered = destinations.all { destination ->
                runCatching { destination.track(event) }.isSuccess
            }

            when {
                allDelivered -> {
                    settledIds += event.eventId
                    attemptCounts.remove(event.eventId)
                }

                else -> {
                    val nextAttempt = (attemptCounts[event.eventId] ?: 0) + 1
                    if (retryPolicy.shouldGiveUp(nextAttempt)) {
                        // Dead-letter: surface it and remove so it can't wedge the queue.
                        onDropped(event, IllegalStateException("Max retry attempts ($nextAttempt) exceeded"))
                        settledIds += event.eventId
                        attemptCounts.remove(event.eventId)
                    } else {
                        // Left in the store; retried on the next dispatch cycle.
                        attemptCounts[event.eventId] = nextAttempt
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
        val DEFAULT_FLUSH_INTERVAL: Duration = 10.seconds
    }
}
