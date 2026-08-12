package com.hopcape.performance.internal.store

import com.hopcape.performance.internal.model.CompletedSpan

// ─────────────────────────────────────────────────────────────
// SpanStore — buffer abstraction (DIP). A real implementation backs
// onto SQLDelight so spans buffered offline survive process death;
// the interface is storage-agnostic so tests and the default
// in-memory implementation stay interchangeable. Mirrors the
// analytics module's EventStore.
// ─────────────────────────────────────────────────────────────
internal interface SpanStore {
    fun enqueue(span: CompletedSpan)
    fun peekBatch(maxSize: Int): List<CompletedSpan>
    fun remove(spanIds: List<String>)
    fun size(): Int
}
