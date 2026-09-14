package com.hopcape.odo.core.platform.config

import com.hopcape.odo.core.config.ConfigSnapshotStore
import platform.Foundation.NSUserDefaults

/**
 * [ConfigSnapshotStore] on `NSUserDefaults`, under one namespaced key.
 *
 * One dictionary rather than a key each, unlike [DefaultsLocalConfigOverrides]:
 * that one is written a key at a time by a QA screen, while this is replaced whole
 * on every refresh. Storing it as a dictionary makes the replace atomic, so a
 * launch during a write cannot read half of one snapshot and half of another.
 */
internal class DefaultsConfigSnapshotStore : ConfigSnapshotStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    @Suppress("UNCHECKED_CAST")
    override fun read(): Map<String, String> {
        val stored = defaults.dictionaryForKey(KEY) ?: return emptyMap()
        return stored.entries.mapNotNull { entry ->
            val key = entry.key as? String
            val value = entry.value as? String
            if (key != null && value != null) key to value else null
        }.toMap()
    }

    override fun write(values: Map<String, String>) {
        defaults.setObject(values as Map<Any?, *>, KEY)
    }

    private companion object {
        const val KEY = "odo_config_snapshot"
    }
}
