package com.hopcape.odo.infrastructure.firebase.remoteconfig

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.android
import kotlinx.coroutines.tasks.await

/**
 * Loads `res/xml/remote_config_defaults.xml` through the real Firebase SDK instance
 * ([FirebaseRemoteConfig.android] — gitlive's escape hatch to it) rather than gitlive's own
 * `setDefaults(vararg)`. [defaults] is unused here on purpose: the XML resource is the
 * canonical source on Android, kept in sync by hand with it — see that file's own comment.
 */
internal actual suspend fun applyLocalDefaults(config: FirebaseRemoteConfig, defaults: Map<String, Any>) {
    config.android.setDefaultsAsync(R.xml.remote_config_defaults).await()
}
