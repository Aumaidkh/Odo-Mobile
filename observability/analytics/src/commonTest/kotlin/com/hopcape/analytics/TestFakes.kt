package com.hopcape.analytics

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.destinations.AnalyticsDestination
import com.hopcape.analytics.internal.model.AnalyticsEvent
import com.hopcape.analytics.internal.model.GlobalContext
import com.hopcape.analytics.internal.store.EventStore

/**
 * Test doubles + builders shared across the analytics suite. They implement the
 * module's (internal) `AnalyticsDestination` and `EventStore` ports so the real
 * pipeline can be exercised without a live vendor SDK — the point of the ports.
 */

/** A fixed context for building events in tests. */
internal fun testContext(): GlobalContext = GlobalContext(
    appVersion = "1.0.0",
    deviceModel = "Pixel-Test",
    osVersion = "Android 14",
    locale = "en-IN",
)

/** Builds a minimal [AnalyticsEvent] with a stable, unique id per call. */
internal fun testEvent(
    name: String,
    properties: Map<String, Any?> = emptyMap(),
    sequenceNumber: Long = 0L,
): AnalyticsEvent = AnalyticsEvent(
    name = name,
    properties = properties,
    context = testContext(),
    sequenceNumber = sequenceNumber,
)

/** An [EventStore] that records operations in a plain list — deterministic for assertions. */
internal class RecordingStore : EventStore {
    val events = mutableListOf<AnalyticsEvent>()

    override fun enqueue(event: AnalyticsEvent) {
        events += event
    }

    override fun peekBatch(maxSize: Int): List<AnalyticsEvent> = events.take(maxSize)

    override fun remove(eventIds: List<String>) {
        val ids = eventIds.toHashSet()
        events.removeAll { it.eventId in ids }
    }

    override fun size(): Int = events.size

    override fun recordAttempt(eventId: String, attempt: Int) {
        val index = events.indexOfFirst { it.eventId == eventId }
        if (index >= 0) events[index] = events[index].copy(attemptCount = attempt)
    }
}

/**
 * An [AnalyticsDestination] that records everything it receives. [throwTimes] makes the
 * first N `track` calls throw (an unexpected crash, e.g. for [SafeDestination] tests);
 * [failTimes] makes the first N calls honestly return `false` (an expected delivery
 * failure, e.g. for [BatchDispatcher] retry/dead-letter tests) — these are deliberately
 * different failure modes now that `track` reports its own outcome.
 */
internal class RecordingDestination(
    override val name: String = "recording",
    private var throwTimes: Int = 0,
    private var failTimes: Int = 0,
) : AnalyticsDestination {

    val tracked = mutableListOf<AnalyticsEvent>()
    val identified = mutableListOf<UserTraits>()
    var flushCount = 0
        private set

    override fun identify(traits: UserTraits) {
        identified += traits
    }

    override fun track(event: AnalyticsEvent): Boolean {
        if (throwTimes > 0) {
            throwTimes--
            throw RuntimeException("destination boom")
        }
        if (failTimes > 0) {
            failTimes--
            return false
        }
        tracked += event
        return true
    }

    override fun flush() {
        flushCount++
    }
}
