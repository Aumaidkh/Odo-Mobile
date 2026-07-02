@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.performance.internal.store

import com.hopcape.performance.internal.model.CompletedSpan
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// InMemorySpanStore — the default, KMP-native buffer. The producer
// (endSpan() on the caller thread) and the consumer (the dispatch
// coroutine) touch it concurrently, so it uses a lock-free
// copy-on-write list behind an AtomicReference with a CAS retry
// loop — no `synchronized`, no `java.util.concurrent`, so it
// compiles unchanged on Android and iOS. Mirrors InMemoryEventStore.
//
// A real production store is a SQLDelight-backed queue so a killed
// process doesn't lose buffered spans; this is the test/dev default.
// ─────────────────────────────────────────────────────────────
internal class InMemorySpanStore : SpanStore {

    private val queue = AtomicReference<List<CompletedSpan>>(emptyList())

    override fun enqueue(span: CompletedSpan) = mutate { it + span }

    override fun peekBatch(maxSize: Int): List<CompletedSpan> = queue.load().take(maxSize)

    override fun remove(spanIds: List<String>) {
        val ids = spanIds.toHashSet()
        mutate { current -> current.filterNot { it.spanId in ids } }
    }

    override fun size(): Int = queue.load().size

    /** Atomically swaps the backing list; the CAS loop makes concurrent enqueue/remove safe. */
    private inline fun mutate(transform: (List<CompletedSpan>) -> List<CompletedSpan>) {
        while (true) {
            val current = queue.load()
            if (queue.compareAndSet(current, transform(current))) return
        }
    }
}
