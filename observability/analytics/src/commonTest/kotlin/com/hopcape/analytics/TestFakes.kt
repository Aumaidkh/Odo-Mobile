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
}

/**
 * An [AnalyticsDestination] that records everything it receives. [failTimes] makes
 * the first N `track` calls throw, so retry/dead-letter paths can be exercised.
 */
internal class RecordingDestination(
    override val name: String = "recording",
    private var failTimes: Int = 0,
) : AnalyticsDestination {

    val tracked = mutableListOf<AnalyticsEvent>()
    val identified = mutableListOf<UserTraits>()
    var flushCount = 0
        private set

    override fun identify(traits: UserTraits) {
        identified += traits
    }

    override fun track(event: AnalyticsEvent) {
        if (failTimes > 0) {
            failTimes--
            throw RuntimeException("destination boom")
        }
        tracked += event
    }

    override fun flush() {
        flushCount++
    }
}
