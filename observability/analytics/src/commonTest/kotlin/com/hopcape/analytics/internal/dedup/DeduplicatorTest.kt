package com.hopcape.analytics.internal.dedup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeduplicatorTest {

    @Test
    fun firstOccurrence_isNotDuplicate_secondIs() {
        val dedup = Deduplicator()
        assertFalse(dedup.isDuplicate("purchase|id=1"))
        assertTrue(dedup.isDuplicate("purchase|id=1"))
    }

    @Test
    fun differentSignatures_areIndependent() {
        val dedup = Deduplicator()
        assertFalse(dedup.isDuplicate("a"))
        assertFalse(dedup.isDuplicate("b"))
        assertTrue(dedup.isDuplicate("a"))
    }

    @Test
    fun signatureEvictedOnceWindowOverflows_isAllowedAgain() {
        val dedup = Deduplicator(windowSize = 2)
        assertFalse(dedup.isDuplicate("a"))
        assertFalse(dedup.isDuplicate("b"))
        // Still within the window — a genuine repeat.
        assertTrue(dedup.isDuplicate("b"))
        // "c" overflows the window of 2 and evicts the oldest ("a").
        assertFalse(dedup.isDuplicate("c"))
        assertFalse(dedup.isDuplicate("a"), "evicted signature should be treated as new again")
    }
}
