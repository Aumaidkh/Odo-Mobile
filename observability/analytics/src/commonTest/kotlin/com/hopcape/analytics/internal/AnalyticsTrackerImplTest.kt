package com.hopcape.analytics.internal

import com.hopcape.analytics.RecordingDestination
import com.hopcape.analytics.RecordingStore
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.dedup.Deduplicator
import com.hopcape.analytics.internal.destinations.AnalyticsDestination
import com.hopcape.analytics.internal.dispatch.BatchDispatcher
import com.hopcape.analytics.internal.store.EventStore
import com.hopcape.analytics.internal.validation.EventRegistry
import com.hopcape.analytics.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnalyticsTrackerImplTest {

    private val schema = EventSchema("purchase", mapOf("id" to PropertyType.STRING))

    private fun tracker(
        strict: Boolean = false,
        store: EventStore,
        destinations: List<AnalyticsDestination> = emptyList(),
        registry: EventRegistry = EventRegistry(listOf(schema)),
        diagnostics: MutableList<String> = mutableListOf(),
    ): AnalyticsTrackerImpl = AnalyticsTrackerImpl(
        registry = registry,
        destinations = destinations,
        store = store,
        dedup = Deduplicator(),
        // Large batch so dispatchIfBatchFull() never launches — the pipeline
        // logic (consent/validation/dedup/enqueue) is exercised synchronously.
        dispatcher = BatchDispatcher(store = store, destinations = destinations, batchSize = 10_000),
        strictSchemaValidation = strict,
        onDiagnostic = { diagnostics += it },
        contextProvider = ::testContext,
    )

    // ── Consent gate ────────────────────────────────────────────

    @Test
    fun track_beforeConsent_isDropped() {
        val store = RecordingStore()
        tracker(store = store).track("purchase", mapOf("id" to "x"))
        assertTrue(store.events.isEmpty(), "no consent → nothing enqueued")
    }

    @Test
    fun track_afterConsentDenied_isDropped() {
        val store = RecordingStore()
        val tracker = tracker(store = store)
        tracker.setConsent(ConsentStatus.DENIED)
        tracker.track("purchase", mapOf("id" to "x"))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun track_afterConsentGranted_enqueuesEnrichedEvent() {
        val store = RecordingStore()
        val tracker = tracker(store = store)
        tracker.setConsent(ConsentStatus.GRANTED)

        tracker.track("purchase", mapOf("id" to "x"))

        val event = store.events.single()
        assertEquals("purchase", event.name)
        assertEquals("1.0.0", event.context.appVersion, "context provider should stamp the event")
        assertTrue(event.sequenceNumber > 0, "a sequence number should be assigned")
    }

    @Test
    fun identify_isGatedOnConsent_thenReachesDestinations() {
        val destination = RecordingDestination()
        val tracker = tracker(store = RecordingStore(), destinations = listOf(destination))

        tracker.identify(UserTraits("u-1"))
        assertTrue(destination.identified.isEmpty(), "identify before consent is dropped")

        tracker.setConsent(ConsentStatus.GRANTED)
        tracker.identify(UserTraits("u-1"))
        assertEquals("u-1", destination.identified.single().userId)
    }

    // ── Dedup ───────────────────────────────────────────────────

    @Test
    fun duplicateEvents_areCollapsed() {
        val store = RecordingStore()
        val tracker = tracker(store = store)
        tracker.setConsent(ConsentStatus.GRANTED)

        tracker.track("purchase", mapOf("id" to "x"))
        tracker.track("purchase", mapOf("id" to "x"))

        assertEquals(1, store.events.size, "the second identical fire should be deduped")
    }

    @Test
    fun differentProperties_areNotDeduped() {
        val store = RecordingStore()
        val tracker = tracker(store = store)
        tracker.setConsent(ConsentStatus.GRANTED)

        tracker.track("purchase", mapOf("id" to "a"))
        tracker.track("purchase", mapOf("id" to "b"))

        assertEquals(2, store.events.size)
    }

    // ── Schema validation policy ────────────────────────────────

    @Test
    fun invalidEvent_inStrictMode_throws_andIsNotEnqueued() {
        val store = RecordingStore()
        val diagnostics = mutableListOf<String>()
        val tracker = tracker(strict = true, store = store, diagnostics = diagnostics)
        tracker.setConsent(ConsentStatus.GRANTED)

        assertFailsWith<IllegalArgumentException> {
            tracker.track("purchase", mapOf("id" to 123)) // wrong type
        }
        assertTrue(store.events.isEmpty())
        assertTrue(diagnostics.isNotEmpty())
    }

    @Test
    fun invalidEvent_inLenientMode_isDropped_notThrown() {
        val store = RecordingStore()
        val diagnostics = mutableListOf<String>()
        val tracker = tracker(strict = false, store = store, diagnostics = diagnostics)
        tracker.setConsent(ConsentStatus.GRANTED)

        tracker.track("purchase", mapOf("id" to 123)) // wrong type, no throw

        assertTrue(store.events.isEmpty())
        assertTrue(diagnostics.isNotEmpty(), "the violation should still be surfaced")
    }

    @Test
    fun unregisteredEvent_inStrictMode_isDropped() {
        val store = RecordingStore()
        val tracker = tracker(strict = true, store = store)
        tracker.setConsent(ConsentStatus.GRANTED)

        tracker.track("mystery", emptyMap())

        assertTrue(store.events.isEmpty(), "unknown events are dropped in debug to enforce discipline")
    }

    @Test
    fun unregisteredEvent_inLenientMode_passesThrough() {
        val store = RecordingStore()
        val tracker = tracker(strict = false, store = store)
        tracker.setConsent(ConsentStatus.GRANTED)

        tracker.track("mystery", emptyMap())

        assertEquals(1, store.events.size, "production must not lose unknown events")
    }
}
