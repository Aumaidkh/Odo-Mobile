package com.hopcape.odo.core.config

/**
 * Every key the app knows about, assembled from the per-module contributions KSP
 * generates.
 *
 * KSP validates within the module it is processing, so it cannot see that two modules
 * declared the same key. This class is where that becomes visible: the first
 * contribution to declare a key wins, and every later one is recorded in
 * [duplicateKeys]. Deciding what to do about it — fail in debug, log in release — is
 * left to the caller rather than taken here, so that one policy lives in one place.
 */
class ConfigRegistry(contributions: List<ConfigContribution>) {

    private val byKey: Map<String, ConfigKey>

    /** Keys declared by more than one contribution, in declaration order. */
    val duplicateKeys: List<String>

    init {
        val accepted = LinkedHashMap<String, ConfigKey>()
        val duplicates = mutableListOf<String>()
        for (contribution in contributions) {
            for (key in contribution.keys) {
                // Not putIfAbsent: that is a JVM-only Map extension, and this module is
                // commonMain-pure.
                if (accepted.containsKey(key.key)) duplicates += key.key else accepted[key.key] = key
            }
        }
        byKey = accepted
        duplicateKeys = duplicates.toList()
    }

    /** Every registered key, in declaration order. The QA screen lists these. */
    val keys: List<ConfigKey> get() = byKey.values.toList()

    fun find(key: String): ConfigKey? = byKey[key]

    /**
     * The descriptor for [key].
     *
     * Throws when the key is not registered, which means the module that declares it
     * was not wired into Koin. That is a build wiring mistake, not a runtime
     * condition, so it is worth failing on rather than papering over — unlike an
     * unreachable backend, which resolves to the compiled default.
     */
    fun require(key: String): ConfigKey = byKey[key]
        ?: error("Config key '$key' is not registered. Is its module's config Koin module installed?")

    /**
     * Every compiled default, keyed by name. This is what the Remote Config SDK is
     * seeded with, and it replaces the hand-maintained defaults maps the adapter used
     * to sum together.
     */
    fun defaults(): Map<String, Any> = byKey.mapValues { (_, descriptor) -> descriptor.default }
}
