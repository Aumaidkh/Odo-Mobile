package com.hopcape.odo.infrastructure.firebase.remoteconfig

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * iOS has no equivalent to Android's `res/xml/remote_config_defaults.xml` convention, so
 * this applies [defaults] directly through gitlive's cross-platform API — the same values
 * that resource holds, kept in sync by hand (MVP is Android-only; this exists so the module
 * still compiles for the iOS targets `:core:*` KMP libraries carry).
 */
internal actual suspend fun applyLocalDefaults(config: FirebaseRemoteConfig, defaults: Map<String, Any>) {
    config.setDefaults(*defaults.map { (key, value) -> key to value }.toTypedArray())
}
