package com.hopcape.odo.core.platform.config

import android.content.Context
import androidx.core.content.edit
import com.hopcape.odo.core.config.LocalConfigOverrides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * [LocalConfigOverrides] on `SharedPreferences` — one file, one string per key.
 *
 * Prefs rather than the database, for the same reason as `PrefsShowcaseSeenStore`: this is
 * device state the record is not made of, so there is no schema and nothing for an
 * installed build to migrate. Values are stored as the raw strings the QA screen types,
 * which is also the form `@Value`'s default is written in — one parsing path serves both.
 *
 * **Only bound in debug builds.** A release build has nothing behind
 * [LocalConfigOverrides] at all, so the override step of the resolution order simply finds
 * nothing.
 */
internal class PrefsLocalConfigOverrides(context: Context) : LocalConfigOverrides {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val changes: Flow<Unit> = _changes

    override fun raw(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, raw: String) {
        prefs.edit { putString(key, raw) }
        _changes.tryEmit(Unit)
    }

    override fun clear(key: String) {
        prefs.edit { remove(key) }
        _changes.tryEmit(Unit)
    }

    override fun clearAll() {
        prefs.edit { clear() }
        _changes.tryEmit(Unit)
    }

    private companion object {
        const val PREFS_NAME = "odo_config_overrides"
    }
}
