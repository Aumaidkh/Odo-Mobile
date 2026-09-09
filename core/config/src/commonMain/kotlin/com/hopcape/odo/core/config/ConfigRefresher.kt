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

    /** For a test that does not care. The graph gets an empty [ChainedConfigSource]. */
    object None : ConfigRefresher {
        override suspend fun refresh() = Unit
    }
}

/**
 * Every read is `null`, so every key resolves to its compiled default.
 *
 * What a build with no backend behaves like, expressed as one object. The graph builds an
 * empty [ChainedConfigSource] for that case rather than binding this; it stays for tests
 * and as the read-only source a chain can be handed.
 */
object NoRemoteConfigSource : ConfigSource {
    override fun boolean(key: String): Boolean? = null
    override fun int(key: String): Int? = null
    override fun long(key: String): Long? = null
    override fun double(key: String): Double? = null
    override fun string(key: String): String? = null
    override val generation: StateFlow<Long> = MutableStateFlow(0L)
}
