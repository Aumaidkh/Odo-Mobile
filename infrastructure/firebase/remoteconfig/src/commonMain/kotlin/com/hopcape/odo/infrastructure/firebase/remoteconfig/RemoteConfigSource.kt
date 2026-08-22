package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.config.ConfigSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the config system with Firebase Remote Config.
 *
 * [FirebaseRemoteConfigGateway] is unchanged: it is already the fake-able seam, and it
 * already turns every SDK failure into a diagnostic rather than a throw. This adds the
 * `ConfigSource` shape on top of it, plus the generation counter the flows are built on.
 *
 * **Every read goes through the string value**, including the numbers. Remote Config
 * stores everything as a string anyway, so this loses nothing, and it gains two things:
 * an unset key reads as `null` rather than as the SDK's zero-or-false, and the parsing is
 * the same parsing used for a compiled default and for a QA override. The one behaviour
 * this does not inherit is the SDK's loose truthiness — `yes`, `on` and `1` are not
 * booleans here, so a key set to one of those falls through to its default rather than
 * being read as true. For keys this app declares and this app's console holds, strict is
 * the safer half of that trade.
 */
internal class RemoteConfigSource(
    private val gateway: FirebaseRemoteConfigGateway,
) : ConfigSource, ConfigRefresher {

    private val _generation = MutableStateFlow(0L)
    override val generation: StateFlow<Long> = _generation.asStateFlow()

    /**
     * Fetches, and bumps the counter only if new values were actually activated.
     *
     * A throttled fetch on a fresh install, an unreachable backend and a fetch that
     * returned nothing new all look the same from here: no bump, so no flow re-reads and
     * no screen re-renders. The gateway swallows the failure itself, which is why there
     * is nothing to catch.
     */
    override suspend fun refresh() {
        if (gateway.fetchAndActivate()) _generation.value += 1
    }

    /**
     * Blank is "no value set", which the contract requires be indistinguishable from absent.
     *
     * Trimmed first, because a value pasted into the console picks up whitespace remarkably
     * often and one leading space is enough to make a mail composer refuse an address or a
     * number fail to parse.
     */
    private fun raw(key: String): String? = gateway.string(key)?.trim()?.takeIf { it.isNotEmpty() }

    override fun boolean(key: String): Boolean? = raw(key)?.toBooleanStrictOrNull()

    override fun int(key: String): Int? = raw(key)?.toIntOrNull()

    override fun long(key: String): Long? = raw(key)?.toLongOrNull()

    override fun double(key: String): Double? = raw(key)?.toDoubleOrNull()

    override fun string(key: String): String? = raw(key)
}
