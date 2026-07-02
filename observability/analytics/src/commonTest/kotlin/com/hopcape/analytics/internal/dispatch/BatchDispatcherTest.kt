@file:OptIn(ExperimentalCoroutinesApi::class)

package com.hopcape.analytics.internal.dispatch

import com.hopcape.analytics.RecordingDestination
import com.hopcape.analytics.internal.model.AnalyticsEvent
import com.hopcape.analytics.internal.store.InMemoryEventStore
import com.hopcape.analytics.testEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchDispatcherTest {

    @Test
    fun flush_deliversQueuedEvents_thenRemovesThemFromStore() = runTest {
        val store = InMemoryEventStore().apply {
            enqueue(testEvent("a"))
            enqueue(testEvent("b"))
        }
        val destination = RecordingDestination()
        val dispatcher = BatchDispatcher(
            store = store,
            destinations = listOf(destination),
            batchSize = 10,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        dispatcher.flushNow()

        assertEquals(listOf("a", "b"), destination.tracked.map { it.name })
        assertEquals(0, store.size(), "delivered events must be removed from the store")
    }

    @Test
    fun failedDelivery_keepsEventForRetry_thenDeadLettersAfterMaxAttempts() = runTest {
        val store = InMemoryEventStore().apply { enqueue(testEvent("stubborn")) }
        val alwaysFails = RecordingDestination(failTimes = Int.MAX_VALUE)
        val dropped = mutableListOf<AnalyticsEvent>()
        val dispatcher = BatchDispatcher(
            store = store,
            destinations = listOf(alwaysFails),
            retryPolicy = RetryPolicy(maxAttempts = 3),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            onDropped = { event, _ -> dropped += event },
        )

        // Attempts 1 and 2: kept for retry, not dropped.
        dispatcher.flushNow()
        assertEquals(1, store.size())
        dispatcher.flushNow()
        assertEquals(1, store.size())
        assertTrue(dropped.isEmpty())

        // Attempt 3 reaches maxAttempts → dead-lettered and removed.
        dispatcher.flushNow()
        assertEquals(0, store.size())
        assertEquals(listOf("stubborn"), dropped.map { it.name })
    }

    @Test
    fun sequenceNumbers_areMonotonic() {
        val dispatcher = BatchDispatcher(store = InMemoryEventStore(), destinations = emptyList())
        val first = dispatcher.nextSequenceNumber()
        val second = dispatcher.nextSequenceNumber()
        assertEquals(first + 1, second)
    }
}
