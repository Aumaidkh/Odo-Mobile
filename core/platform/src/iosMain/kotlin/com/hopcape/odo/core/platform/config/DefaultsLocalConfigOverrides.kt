package com.hopcape.odo.core.platform.config

import com.hopcape.odo.core.config.LocalConfigOverrides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.Foundation.NSUserDefaults

/**
 * [LocalConfigOverrides] on `NSUserDefaults` — the iOS mirror of
 * [com.hopcape.odo.core.platform.config.PrefsLocalConfigOverrides]. Namespaced keys rather
 * than a separate suite, the simplest thing that persists.
 *
 * [clearAll] can only remove the keys it is told about, because `NSUserDefaults` has no
 * per-prefix wipe; the caller passes the registry's key list.
 */
internal class DefaultsLocalConfigOverrides(
    private val knownKeys: () -> List<String>,
) : LocalConfigOverrides {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val changes: Flow<Unit> = _changes

    override fun raw(key: String): String? = defaults.stringForKey(namespaced(key))

    override fun set(key: String, raw: String) {
        defaults.setObject(raw, namespaced(key))
        _changes.tryEmit(Unit)
    }

    override fun clear(key: String) {
        defaults.removeObjectForKey(namespaced(key))
        _changes.tryEmit(Unit)
    }

    override fun clearAll() {
        knownKeys().forEach { defaults.removeObjectForKey(namespaced(it)) }
        _changes.tryEmit(Unit)
    }

    private fun namespaced(key: String) = "odo_config_override_$key"
}
