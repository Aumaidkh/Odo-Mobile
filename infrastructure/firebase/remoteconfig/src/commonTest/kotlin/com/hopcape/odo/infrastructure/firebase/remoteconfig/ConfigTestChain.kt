package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigContribution
import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.config.ConfigResolver
import kotlin.time.Instant

/**
 * A console that a test can edit, standing in for Firebase.
 *
 * Values are held as the strings Remote Config actually stores, so "the console holds a
 * value with a stray space" and "the key was never set" are expressible the way they really
 * occur.
 */
internal class FakeGateway(
    private val values: MutableMap<String, String> = mutableMapOf(),
    override val lastFetchAt: Instant? = null,
) : FirebaseRemoteConfigGateway {
    override suspend fun fetchAndActivate(): Boolean = true
    override fun long(key: String): Long? = values[key]?.toLongOrNull()
    override fun string(key: String): String? = values[key]

    /** Mimics a console edit that a later fetch activated. */
    operator fun set(key: String, value: String) {
        values[key] = value
    }
}

/**
 * The real chain, with only Firebase itself faked: the generated contribution, the real
 * registry, the real resolver, and the real [RemoteConfigSource] over a fake gateway.
 *
 * Building it this way rather than faking the config interface keeps the behaviour that
 * moved out of the consumers during the migration — trimming, blank-means-absent, enum
 * canonicalisation, failing open on an unrecognised value — under test where it now lives.
 */
internal fun resolverOver(
    gateway: FakeGateway,
    contribution: ConfigContribution,
): ConfigResolver = ConfigResolver(
    registry = ConfigRegistry(listOf(contribution)),
    source = RemoteConfigSource(gateway),
)
