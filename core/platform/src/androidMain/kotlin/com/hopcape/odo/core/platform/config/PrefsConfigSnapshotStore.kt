package com.hopcape.odo.core.platform.config

import android.content.Context
import androidx.core.content.edit
import com.hopcape.odo.core.config.ConfigSnapshotStore

/**
 * [ConfigSnapshotStore] on `SharedPreferences` — one file, one string per key.
 *
 * Prefs rather than the database, for the same reason as [PrefsLocalConfigOverrides]:
 * this is device state the record is not made of, so there is no schema and nothing
 * for an installed build to migrate. Losing it costs one launch resolved from
 * compiled defaults, which is a correct outcome rather than a broken one.
 *
 * [write] replaces the whole file rather than merging. A key that stops being sent
 * has stopped being overridden, and merging would leave the device honouring an
 * override the backend no longer has — which is the one failure a config cache must
 * not have.
 */
internal class PrefsConfigSnapshotStore(context: Context) : ConfigSnapshotStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    override fun write(values: Map<String, String>) {
        prefs.edit {
            clear()
            values.forEach { (key, value) -> putString(key, value) }
        }
    }

    private companion object {
        const val PREFS_NAME = "odo_config_snapshot"
    }
}
