package com.hopcape.odo.core.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Asks the backing source for new values.
 *
 * Called once when the process comes to the foreground, which on a cold start is also
 * launch. Reading a key never fetches on its own: a screen that read three keys would
 * otherwise trigger three fetches, and the answer would change under it mid-frame.
 *
 * [refresh] must never throw. A backend that cannot be reached is a backend with nothing
 * new to say, and every read still resolves to the last activated value or the compiled
 * default.
 */
interface ConfigRefresher {

    suspend fun refresh()

    /** For builds with no backend wired: iOS today, and any test that does not care. */
    object None : ConfigRefresher {
        override suspend fun refresh() = Unit
    }
}

/**
 * The answer when nothing remote is configured. Every read is `null`, so every key
 * resolves to its compiled default.
 *
 * This is a real answer rather than a placeholder. An install that never reaches the
 * backend behaves exactly like this for its whole life, and so does a build with no
 * Firebase project — which is what iOS is today.
 */
object NoRemoteConfigSource : ConfigSource {
    override fun boolean(key: String): Boolean? = null
    override fun int(key: String): Int? = null
    override fun long(key: String): Long? = null
    override fun double(key: String): Double? = null
    override fun string(key: String): String? = null
    override val generation: StateFlow<Long> = MutableStateFlow(0L)
}
