package com.hopcape.odo.infrastructure.firebase.remoteconfig

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * Applies the safe, non-blocking answer a fresh install has before its first network fetch.
 *
 * One implementation for every platform, and [defaults] is the only copy of those values.
 *
 * Android used to have its own: a checked-in `res/xml/remote_config_defaults.xml`, loaded
 * through the real SDK's `setDefaultsAsync(int)` because that is Firebase's documented
 * Android convention. That resource was a fourth hand-maintained copy of what the
 * `REMOTE_DEFAULTS` maps already held, and its own comment said as much — "Kept in sync by
 * hand … Change one, change both." gitlive's cross-platform `setDefaults(vararg)` reaches
 * the same SDK on Android, which is what iOS had been doing the whole time, so the resource
 * and the expect/actual pair around it had nothing left to justify them.
 */
internal suspend fun applyLocalDefaults(config: FirebaseRemoteConfig, defaults: Map<String, Any>) {
    config.setDefaults(*defaults.map { (key, value) -> key to value }.toTypedArray())
}
