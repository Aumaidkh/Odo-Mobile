package com.hopcape.odo.infrastructure.firebase.remoteconfig

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * Applies the safe, non-blocking answer a fresh install has before its first network fetch.
 *
 * Platform-specific because Android has its own native convention for this — a checked-in
 * `res/xml/remote_config_defaults.xml`, loaded through the real SDK's
 * `setDefaultsAsync(int)` — that gitlive's cross-platform `setDefaults(vararg)` has no way
 * to reach (see `LocalRemoteConfigDefaults.android.kt`). Every other platform falls back to
 * [defaults] directly, the same values that resource holds — see that file's own comment for
 * the "kept in sync by hand" note.
 */
internal expect suspend fun applyLocalDefaults(config: FirebaseRemoteConfig, defaults: Map<String, Any>)
