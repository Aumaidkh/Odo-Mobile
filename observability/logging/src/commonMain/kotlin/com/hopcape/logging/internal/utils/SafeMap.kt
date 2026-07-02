package com.hopcape.logging.internal.utils

import kotlin.concurrent.Volatile

/**
 * Minimal thread-safe map for the runtime tag-level overrides — a rare-write,
 * frequent-read structure sitting on the logging hot path. Copy-on-write behind a
 * `@Volatile` reference: reads are lock-free and never block a `log()` call; the
 * occasional write swaps the whole map. A concurrent write is last-writer-wins,
 * which is fine for remote-config toggles and can never crash a caller.
 *
 * Deliberately synchronous (no coroutines/`Mutex`) — logging must not be `suspend`.
 */
internal class SafeMap<K, V> {
    @Volatile
    private var snapshot: Map<K, V> = emptyMap()

    operator fun get(key: K): V? = snapshot[key]

    operator fun set(key: K, value: V) {
        snapshot = snapshot + (key to value)
    }

    fun remove(key: K) {
        snapshot = snapshot - key
    }
}
