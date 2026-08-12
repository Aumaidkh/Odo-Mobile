package com.hopcape.analytics.api

// ─────────────────────────────────────────────────────────────
// AnalyticsEventStore — the port an outside module implements to back the
// pipeline's event queue with real storage (e.g. SQLDelight), so a killed
// process doesn't lose events buffered between track() and the vendor SDK
// finally taking them. Suspend, matching every other local-write port in
// the app — a durable implementation's own I/O runs on whatever dispatcher
// its caller provides, not on the thread that called track().
//
// Configured via AnalyticsConfig.eventStore as a provider, not an instance
// — see that KDoc for why.
//
// An implementation is expected to cap itself — unbounded growth from a
// destination that never recovers is a real failure mode, not a hypothetical
// one. There is no size/age limit in this contract because enforcing it is
// the implementation's job (e.g. evict oldest past a row cap, past an age
// cap, on the same enqueue()/pass that writes the new row) — the port only
// promises FIFO ordering and durability, not a specific eviction policy.
// ─────────────────────────────────────────────────────────────
interface AnalyticsEventStore {
    suspend fun enqueue(event: StoredAnalyticsEvent)
    suspend fun peekBatch(maxSize: Int): List<StoredAnalyticsEvent>
    suspend fun remove(eventIds: List<String>)
    suspend fun size(): Int

    /** Persists a failed delivery's new attempt count, so dead-lettering survives a restart. */
    suspend fun recordAttempt(eventId: String, attempt: Int)
}

/**
 * An event as it crosses the module boundary into a durable store — everything needed to
 * persist it and later hand it back for delivery. Mirrors the internal event model
 * field-for-field, kept as its own public type so persistence code never has to see
 * internal pipeline types.
 */
data class StoredAnalyticsEvent(
    val eventId: String,
    val name: String,
    val properties: Map<String, Any?>,
    val sequenceNumber: Long,
    val timestampMs: Long,
    val context: StoredAnalyticsContext,
    val attemptCount: Int = 0,
)

/** The "super properties" attached to an event when it was first recorded. */
data class StoredAnalyticsContext(
    val appVersion: String,
    val platform: String,
    val deviceModel: String,
    val osVersion: String,
    val locale: String,
    val sessionId: String?,
    val anonymousId: String,
    val userId: String?,
)
