@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.analytics.internal.dedup

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// Deduplicator — collapses accidental double-fires (e.g. a
// double-tap on "Buy Now" firing purchase_completed twice). It is
// keyed on a caller-supplied *content signature* (event name +
// sorted properties) within a bounded recency window, so memory
// stays flat. State lives in a lock-free copy-on-write
// LinkedHashSet behind an AtomicReference with a CAS loop — no
// `java.util.concurrent`, so it is pure commonMain.
//
// Trade-off: two *intentionally* identical events fired inside the
// window collapse to one. If every fire must count, attach a unique
// property (timestamp/nonce) so the signatures differ.
// ─────────────────────────────────────────────────────────────
internal class Deduplicator(private val windowSize: Int = DEFAULT_WINDOW_SIZE) {

    // Insertion-ordered set: iteration order is eviction order (oldest first).
    private val seen = AtomicReference<Set<String>>(emptySet())

    /** Returns true if [signature] was seen within the window; records it otherwise. */
    fun isDuplicate(signature: String): Boolean {
        while (true) {
            val current = seen.load()
            if (signature in current) return true

            val next = LinkedHashSet(current).apply {
                add(signature)
                while (size > windowSize) remove(first())
            }
            if (seen.compareAndSet(current, next)) return false
        }
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 500
    }
}
