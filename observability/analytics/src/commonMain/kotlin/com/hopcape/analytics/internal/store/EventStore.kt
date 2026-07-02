package com.hopcape.analytics.internal.store

import com.hopcape.analytics.internal.model.AnalyticsEvent

// ─────────────────────────────────────────────────────────────
// EventStore — persistence abstraction (DIP). A real implementation
// backs onto SQLDelight/SQLite so buffered events survive process
// death (offline-first). Interface kept storage-agnostic so tests
// and the default in-memory implementation stay interchangeable.
// ─────────────────────────────────────────────────────────────
internal interface EventStore {
    fun enqueue(event: AnalyticsEvent)
    fun peekBatch(maxSize: Int): List<AnalyticsEvent>
    fun remove(eventIds: List<String>)
    fun size(): Int
}
