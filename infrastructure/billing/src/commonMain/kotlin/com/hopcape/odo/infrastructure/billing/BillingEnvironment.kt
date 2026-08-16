package com.hopcape.odo.infrastructure.billing

/**
 * Whether this build can talk to RevenueCat, and the key it talks with.
 *
 * A value rather than an object reading the generated constants directly, for the same
 * reason `SupabaseEnvironment` is one: both branches — configured and not — have to be
 * testable without depending on what happens to be in a developer's `local.properties`.
 *
 * A checkout with no credentials generates a blank. That is a supported state, not a broken
 * one: Odo builds and runs without them, and [isConfigured] is what everything downstream
 * reads to leave the free-plan entitlement in place rather than reaching for a store that
 * will refuse it.
 */
internal data class BillingEnvironment(val apiKey: String) {

    /** Whether there is a key to configure the SDK with. */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {

        /** The key this build was compiled with, for the store it ships to. */
        fun fromBuild(): BillingEnvironment = BillingEnvironment(apiKey = storeApiKey)
    }
}

/**
 * The key for this target's store — Play on Android, the App Store on iOS.
 *
 * Which one to use is a platform question: a Play key is meaningless on iOS and vice versa.
 * Answered per target rather than by a caller branching on a platform it should not have to
 * know about.
 */
internal expect val storeApiKey: String
