@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.analytics.internal.store

import com.hopcape.analytics.internal.model.AnalyticsEvent
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// InMemoryEventStore — the default, KMP-native store. The producer
// (track() on the caller thread) and the consumer (the dispatch
// coroutine) touch it concurrently, so it uses a lock-free
// copy-on-write list behind an AtomicReference with a CAS retry
// loop — no `synchronized`, no `java.util.concurrent`, so it
// compiles unchanged on Android and iOS.
//
// A real production store is a SQLDelight-backed queue (event_id PK,
// json_payload, created_at, retry_count) so a killed process doesn't
// lose buffered events; this in-memory version is the test/dev default.
// ─────────────────────────────────────────────────────────────
internal class InMemoryEventStore : EventStore {

    private val queue = AtomicReference<List<AnalyticsEvent>>(emptyList())

    override fun enqueue(event: AnalyticsEvent) = mutate { it + event }

    override fun peekBatch(maxSize: Int): List<AnalyticsEvent> = queue.load().take(maxSize)

    override fun remove(eventIds: List<String>) {
        val ids = eventIds.toHashSet()
        mutate { current -> current.filterNot { it.eventId in ids } }
    }

    override fun size(): Int = queue.load().size

    /** Atomically swaps the backing list; the CAS loop makes concurrent enqueue/remove safe. */
    private inline fun mutate(transform: (List<AnalyticsEvent>) -> List<AnalyticsEvent>) {
        while (true) {
            val current = queue.load()
            if (queue.compareAndSet(current, transform(current))) return
        }
    }
}
