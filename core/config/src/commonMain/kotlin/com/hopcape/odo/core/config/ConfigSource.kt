package com.hopcape.odo.core.config

import kotlinx.coroutines.flow.StateFlow

/**
 * Where remotely-set values come from. One implementation ships: the Firebase Remote
 * Config adapter. It is an interface so a test can answer without a network, and so a
 * different backend stays possible later.
 *
 * **Every read returns `null` when the key has no remote value.** That covers a key
 * never set in the console, a fresh install whose first fetch has not landed, and a
 * device that cannot reach the backend. An implementation must also map a blank
 * string to `null`: blank means "no override", not "the empty string".
 *
 * A read must never throw. A backend that fails is a backend with nothing to say, and
 * [ConfigResolver] then falls through to the compiled default.
 */
interface ConfigSource {

    fun boolean(key: String): Boolean?

    fun int(key: String): Int?

    fun long(key: String): Long?

    fun double(key: String): Double?

    fun string(key: String): String?

    /**
     * Increments after every fetch that activated new values. One counter for the
     * whole app: [ConfigResolver.observe] maps it through a key read, so adding a key
     * costs no new plumbing.
     */
    val generation: StateFlow<Long>
}

/**
 * Debug-only per-device overrides, set from the QA screen, and first in the
 * resolution order.
 *
 * Values are held as raw strings, the same form [Value.default] is written in, so one
 * parsing path serves both. A string that does not parse for its key's type is
 * ignored, and resolution continues to the remote value.
 *
 * Release builds have no store behind this. The resolver takes it as a nullable
 * dependency rather than checking the build type, so nothing about the resolution
 * order changes between variants — there is simply nothing to find.
 */
interface LocalConfigOverrides {

    fun raw(key: String): String?

    fun set(key: String, raw: String)

    fun clear(key: String)

    fun clearAll()

    /** Emits after every write, so [ConfigResolver.observe] can re-read. */
    val changes: kotlinx.coroutines.flow.Flow<Unit>
}
