@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.analytics.internal.store

import com.hopcape.analytics.api.AnalyticsEventStore
import com.hopcape.analytics.api.StoredAnalyticsContext
import com.hopcape.analytics.api.StoredAnalyticsEvent
import com.hopcape.analytics.internal.model.AnalyticsEvent
import com.hopcape.analytics.internal.model.GlobalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// PublicEventStoreAdapter — bridges the public, suspend-based
// AnalyticsEventStore a host configures to the internal, synchronous
// EventStore contract AnalyticsTrackerImpl/BatchDispatcher already use.
// Never throws into either of them — a durable store failing is the same
// "vendor can never crash the host" guarantee SafeDestination gives
// destinations, applied to storage instead.
//
// enqueue() and size() are genuinely non-blocking (fire-and-forget /
// a local counter): track() is a public, synchronous, fire-and-forget
// call used from arbitrary threads — often the UI thread reacting to a
// tap — and a durable store's first resolution can mean building a
// database. peekBatch()/remove() block instead: every caller is already
// BatchDispatcher's own background coroutine (start()'s timer loop,
// dispatchIfBatchFull(), flushNow() all wrap dispatchOnce() in
// scope.launch), never the UI thread, so a bounded wait for a suspend
// call is the honest, simple choice over a second dispatch mechanism.
// ─────────────────────────────────────────────────────────────
internal class PublicEventStoreAdapter(
    provider: () -> AnalyticsEventStore,
    private val scope: CoroutineScope,
    private val onDiagnostic: (String) -> Unit = {},
) : EventStore {

    private val delegate: AnalyticsEventStore by lazy(provider)

    /**
     * A local approximation of the queue's size, so [size] — called synchronously from
     * the tracker's calling thread via `dispatchIfBatchFull()` — never blocks waiting on
     * the durable store, and never triggers its first resolution either. Being briefly
     * stale just means the batch-size trigger fires a beat late; the periodic timer and
     * an explicit flush are the backstops that make this harmless.
     */
    private val approximateSize = AtomicInt(0)

    override fun enqueue(event: AnalyticsEvent) {
        approximateSize.addAndFetch(1)
        scope.launch {
            runCatching { delegate.enqueue(event.toStored()) }
                .onFailure { onDiagnostic("event store: enqueue failed — ${it::class.simpleName}") }
        }
    }

    override fun peekBatch(maxSize: Int): List<AnalyticsEvent> =
        runCatching { runBlocking { delegate.peekBatch(maxSize) } }
            .onFailure { onDiagnostic("event store: peekBatch failed — ${it::class.simpleName}") }
            .getOrDefault(emptyList())
            .map { it.toInternal() }

    override fun remove(eventIds: List<String>) {
        approximateSize.addAndFetch(-eventIds.size)
        runCatching { runBlocking { delegate.remove(eventIds) } }
            .onFailure { onDiagnostic("event store: remove failed — ${it::class.simpleName}") }
    }

    override fun size(): Int = approximateSize.load()

    // Blocking, like peekBatch/remove: called only from BatchDispatcher's dispatchOnce(),
    // already on the background scope, and ordering matters here — the next dispatch pass
    // must see this attempt count, so a fire-and-forget write is the wrong tradeoff.
    override fun recordAttempt(eventId: String, attempt: Int) {
        runCatching { runBlocking { delegate.recordAttempt(eventId, attempt) } }
            .onFailure { onDiagnostic("event store: recordAttempt failed — ${it::class.simpleName}") }
    }
}

private fun AnalyticsEvent.toStored() = StoredAnalyticsEvent(
    eventId = eventId,
    name = name,
    properties = properties,
    sequenceNumber = sequenceNumber,
    timestampMs = timestampMs,
    context = context.toStored(),
    attemptCount = attemptCount,
)

private fun GlobalContext.toStored() = StoredAnalyticsContext(
    appVersion = appVersion,
    platform = platform,
    deviceModel = deviceModel,
    osVersion = osVersion,
    locale = locale,
    sessionId = sessionId,
    anonymousId = anonymousId,
    userId = userId,
)

private fun StoredAnalyticsEvent.toInternal() = AnalyticsEvent(
    name = name,
    properties = properties,
    context = context.toInternal(),
    sequenceNumber = sequenceNumber,
    eventId = eventId,
    timestampMs = timestampMs,
    attemptCount = attemptCount,
)

private fun StoredAnalyticsContext.toInternal() = GlobalContext(
    appVersion = appVersion,
    platform = platform,
    deviceModel = deviceModel,
    osVersion = osVersion,
    locale = locale,
    sessionId = sessionId,
    anonymousId = anonymousId,
    userId = userId,
)
