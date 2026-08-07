package com.hopcape.analytics.internal.store

import com.hopcape.analytics.api.AnalyticsEventStore
import com.hopcape.analytics.api.StoredAnalyticsEvent
import com.hopcape.analytics.testEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicEventStoreAdapterTest {

    /** Records calls; [enqueueFails]/[readFails] simulate a durable store's own failures. */
    private class RecordingPublicStore(
        var enqueueFails: Boolean = false,
        var readFails: Boolean = false,
    ) : AnalyticsEventStore {
        val stored = mutableListOf<StoredAnalyticsEvent>()

        override suspend fun enqueue(event: StoredAnalyticsEvent) {
            if (enqueueFails) throw IllegalStateException("disk full")
            stored += event
        }

        override suspend fun peekBatch(maxSize: Int): List<StoredAnalyticsEvent> {
            if (readFails) throw IllegalStateException("db closed")
            return stored.take(maxSize)
        }

        override suspend fun remove(eventIds: List<String>) {
            if (readFails) throw IllegalStateException("db closed")
            val ids = eventIds.toHashSet()
            stored.removeAll { it.eventId in ids }
        }

        override suspend fun size(): Int = stored.size

        override suspend fun recordAttempt(eventId: String, attempt: Int) {
            if (readFails) throw IllegalStateException("db closed")
            val index = stored.indexOfFirst { it.eventId == eventId }
            if (index >= 0) stored[index] = stored[index].copy(attemptCount = attempt)
        }
    }

    /**
     * [Dispatchers.Unconfined]: neither of these test bodies suspends for real, so the
     * launched enqueue() coroutine runs to completion inline, before the call returns —
     * exactly what makes the assertions below deterministic without needing runTest.
     */
    private fun adapter(
        delegate: RecordingPublicStore,
        diagnostics: MutableList<String> = mutableListOf(),
    ) = PublicEventStoreAdapter(
        provider = { delegate },
        scope = CoroutineScope(Dispatchers.Unconfined),
        onDiagnostic = { diagnostics += it },
    )

    @Test
    fun enqueue_reachesTheDelegate() {
        val delegate = RecordingPublicStore()
        val sut = adapter(delegate)

        sut.enqueue(testEvent("bill_scanned"))

        assertEquals("bill_scanned", delegate.stored.single().name)
    }

    @Test
    fun peekBatch_andRemove_roundTripThroughTheDelegate() {
        val delegate = RecordingPublicStore()
        val sut = adapter(delegate)
        sut.enqueue(testEvent("a"))
        sut.enqueue(testEvent("b"))

        val batch = sut.peekBatch(10)
        assertEquals(listOf("a", "b"), batch.map { it.name })

        sut.remove(batch.map { it.eventId })
        assertTrue(sut.peekBatch(10).isEmpty())
    }

    @Test
    fun size_reflectsALocalCounter_notARealQuery() {
        // size() reads approximateSize only — never calls delegate.size() — so it stays
        // correct even here, where the delegate would throw if it were ever asked.
        val delegate = RecordingPublicStore(readFails = true)
        val sut = adapter(delegate)

        assertEquals(0, sut.size())
        sut.enqueue(testEvent("a"))
        assertEquals(1, sut.size())
        sut.enqueue(testEvent("b"))
        assertEquals(2, sut.size())
    }

    @Test
    fun size_decreasesOnRemove() {
        val delegate = RecordingPublicStore()
        val sut = adapter(delegate)
        sut.enqueue(testEvent("a"))
        sut.enqueue(testEvent("b"))

        sut.remove(listOf(sut.peekBatch(10).first().eventId))

        assertEquals(1, sut.size())
    }

    @Test
    fun enqueueFailure_isSwallowedAndReported_notThrown() {
        val delegate = RecordingPublicStore(enqueueFails = true)
        val diagnostics = mutableListOf<String>()
        val sut = adapter(delegate, diagnostics)

        // Must not throw:
        sut.enqueue(testEvent("e"))

        assertTrue(diagnostics.single().contains("enqueue failed"))
    }

    @Test
    fun peekBatchFailure_isSwallowedAndReported_returnsEmpty() {
        val delegate = RecordingPublicStore(readFails = true)
        val diagnostics = mutableListOf<String>()
        val sut = adapter(delegate, diagnostics)

        val batch = sut.peekBatch(10)

        assertTrue(batch.isEmpty())
        assertTrue(diagnostics.single().contains("peekBatch failed"))
    }

    @Test
    fun removeFailure_isSwallowedAndReported_notThrown() {
        val delegate = RecordingPublicStore(readFails = true)
        val diagnostics = mutableListOf<String>()
        val sut = adapter(delegate, diagnostics)

        // Must not throw:
        sut.remove(listOf("missing-id"))

        assertTrue(diagnostics.single().contains("remove failed"))
    }

    @Test
    fun recordAttempt_updatesTheDelegate_visibleOnNextPeekBatch() {
        val delegate = RecordingPublicStore()
        val sut = adapter(delegate)
        val id = sut.also { it.enqueue(testEvent("stubborn")) }.peekBatch(10).single().eventId

        sut.recordAttempt(id, attempt = 2)

        assertEquals(2, sut.peekBatch(10).single().attemptCount)
    }

    @Test
    fun recordAttemptFailure_isSwallowedAndReported_notThrown() {
        val delegate = RecordingPublicStore(readFails = true)
        val diagnostics = mutableListOf<String>()
        val sut = adapter(delegate, diagnostics)

        // Must not throw:
        sut.recordAttempt("missing-id", attempt = 1)

        assertTrue(diagnostics.single().contains("recordAttempt failed"))
    }

    @Test
    fun eventRoundTrips_withItsContextIntact() {
        val delegate = RecordingPublicStore()
        val sut = adapter(delegate)

        sut.enqueue(testEvent("purchase", mapOf("id" to "x")))

        val restored = sut.peekBatch(10).single()
        assertEquals("purchase", restored.name)
        assertEquals("x", restored.properties["id"])
        assertEquals("1.0.0", restored.context.appVersion, "context must survive the round trip")
    }
}
