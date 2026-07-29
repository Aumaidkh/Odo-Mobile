@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.crashreporting.internal.breadcrumb

import com.hopcape.crashreporting.internal.model.Breadcrumb
import kotlin.time.Clock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// BreadcrumbTrail — a bounded ring buffer of the most recent
// breadcrumbs. The sketch used a `@Synchronized ArrayDeque`, but
// that pins the module to the JVM; this uses the same lock-free
// copy-on-write AtomicReference + CAS retry loop as the APM
// InMemorySpanStore, so it compiles unchanged on Android and iOS.
//
// Bounded on purpose: crumbs are added on every notable app action,
// so an unbounded trail would leak memory over a long session. Past
// [maxSize] the oldest crumb is dropped (FIFO).
// ─────────────────────────────────────────────────────────────
internal class BreadcrumbTrail(private val maxSize: Int = 50) {

    private val trail = AtomicReference<List<Breadcrumb>>(emptyList())

    /** Appends a crumb stamped with the current wall clock, evicting the oldest past [maxSize]. */
    fun add(tag: String, message: String) {
        val crumb = Breadcrumb(Clock.System.now().toEpochMilliseconds(), tag, message)
        mutate { current ->
            val appended = current + crumb
            if (appended.size > maxSize) appended.takeLast(maxSize) else appended
        }
    }

    /** Immutable snapshot of the current trail, oldest first — attached to a report. */
    fun snapshot(): List<Breadcrumb> = trail.load()

    /** Drops all crumbs (e.g. after a new session begins). */
    fun clear() {
        trail.store(emptyList())
    }

    /** Atomically swaps the backing list; the CAS loop makes concurrent add/clear safe. */
    private inline fun mutate(transform: (List<Breadcrumb>) -> List<Breadcrumb>) {
        while (true) {
            val current = trail.load()
            if (trail.compareAndSet(current, transform(current))) return
        }
    }
}
