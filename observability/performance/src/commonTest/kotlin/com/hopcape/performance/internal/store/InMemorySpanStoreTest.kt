package com.hopcape.performance.internal.store

import com.hopcape.performance.testSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemorySpanStoreTest {

    @Test
    fun enqueue_increasesSize_andPeekReturnsInOrder() {
        val store = InMemorySpanStore()
        store.enqueue(testSpan("a", "1"))
        store.enqueue(testSpan("b", "2"))

        assertEquals(2, store.size())
        assertEquals(listOf("a", "b"), store.peekBatch(10).map { it.name })
    }

    @Test
    fun peekBatch_respectsMaxSize_withoutRemoving() {
        val store = InMemorySpanStore()
        repeat(5) { store.enqueue(testSpan("s$it", "$it")) }

        assertEquals(2, store.peekBatch(2).size)
        assertEquals(5, store.size(), "peek must not consume")
    }

    @Test
    fun remove_dropsOnlyMatchingIds() {
        val store = InMemorySpanStore()
        store.enqueue(testSpan("a", "1"))
        store.enqueue(testSpan("b", "2"))
        store.enqueue(testSpan("c", "3"))

        store.remove(listOf("1", "3"))

        assertEquals(listOf("b"), store.peekBatch(10).map { it.name })
    }
}
