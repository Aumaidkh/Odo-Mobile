@file:OptIn(ExperimentalCoroutinesApi::class)

package com.hopcape.performance.internal.dispatch

import com.hopcape.performance.RecordingSpanExporter
import com.hopcape.performance.internal.model.CompletedSpan
import com.hopcape.performance.internal.store.InMemorySpanStore
import com.hopcape.performance.testSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchSpanDispatcherTest {

    @Test
    fun flush_deliversQueuedSpans_thenRemovesThemFromStore() = runTest {
        val store = InMemorySpanStore().apply {
            enqueue(testSpan("a", "1"))
            enqueue(testSpan("b", "2"))
        }
        val exporter = RecordingSpanExporter()
        val dispatcher = BatchSpanDispatcher(
            store = store,
            exporters = listOf(exporter),
            batchSize = 10,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        dispatcher.flushNow()

        assertEquals(listOf("a", "b"), exporter.exported.map { it.name })
        assertEquals(0, store.size(), "delivered spans must be removed from the store")
    }

    @Test
    fun failedDelivery_keepsSpanForRetry_thenDeadLettersAfterMaxAttempts() = runTest {
        val store = InMemorySpanStore().apply { enqueue(testSpan("stubborn", "1")) }
        val alwaysFails = RecordingSpanExporter(failTimes = Int.MAX_VALUE)
        val dropped = mutableListOf<CompletedSpan>()
        val dispatcher = BatchSpanDispatcher(
            store = store,
            exporters = listOf(alwaysFails),
            retryPolicy = RetryPolicy(maxAttempts = 3),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            onDropped = { span, _ -> dropped += span },
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
    fun flush_asksExportersToPersist() = runTest {
        val exporter = RecordingSpanExporter()
        val dispatcher = BatchSpanDispatcher(
            store = InMemorySpanStore(),
            exporters = listOf(exporter),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        dispatcher.flushNow()

        assertEquals(1, exporter.flushCount)
    }

    @Test
    fun sequenceNumbers_areMonotonic() {
        val dispatcher = BatchSpanDispatcher(store = InMemorySpanStore(), exporters = emptyList())
        val first = dispatcher.nextSequenceNumber()
        val second = dispatcher.nextSequenceNumber()
        assertEquals(first + 1, second)
    }
}
