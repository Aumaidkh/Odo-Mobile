package com.hopcape.analytics.internal.store

import com.hopcape.analytics.testEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryEventStoreTest {

    @Test
    fun enqueue_increasesSize_andPreservesOrder() {
        val store = InMemoryEventStore()
        store.enqueue(testEvent("a"))
        store.enqueue(testEvent("b"))

        assertEquals(2, store.size())
        assertEquals(listOf("a", "b"), store.peekBatch(10).map { it.name })
    }

    @Test
    fun peekBatch_isBoundedAndFifo() {
        val store = InMemoryEventStore()
        repeat(5) { store.enqueue(testEvent("e$it")) }

        assertEquals(listOf("e0", "e1"), store.peekBatch(2).map { it.name })
    }

    @Test
    fun remove_deletesOnlyMatchingIds() {
        val store = InMemoryEventStore()
        val keep = testEvent("keep")
        val drop = testEvent("drop")
        store.enqueue(keep)
        store.enqueue(drop)

        store.remove(listOf(drop.eventId))

        assertEquals(1, store.size())
        assertEquals("keep", store.peekBatch(10).single().name)
    }

    @Test
    fun peekBatch_onEmptyStore_isEmpty() {
        assertTrue(InMemoryEventStore().peekBatch(10).isEmpty())
    }

    @Test
    fun recordAttempt_updatesOnlyTheMatchingEvent() {
        val store = InMemoryEventStore()
        val a = testEvent("a")
        val b = testEvent("b")
        store.enqueue(a)
        store.enqueue(b)

        store.recordAttempt(a.eventId, attempt = 2)

        val byId = store.peekBatch(10).associateBy { it.eventId }
        assertEquals(2, byId.getValue(a.eventId).attemptCount)
        assertEquals(0, byId.getValue(b.eventId).attemptCount)
    }
}
