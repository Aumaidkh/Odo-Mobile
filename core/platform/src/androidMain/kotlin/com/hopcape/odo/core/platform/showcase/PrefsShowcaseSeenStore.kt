package com.hopcape.odo.core.platform.showcase

import android.content.Context
import androidx.core.content.edit
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore

/**
 * [ShowcaseSeenStore] on `SharedPreferences` — one file, one boolean per hook.
 *
 * Prefs rather than the database (owner's call, docs/SHOWCASE_PLAN.md decision 1): this
 * is device state the record is not made of, the same class as `PrefsVehicleBondStore`'s
 * bond and the permission controller's asked-once flags. No schema means no `.sqm` and
 * nothing for an installed 1.0 to migrate.
 */
internal class PrefsShowcaseSeenStore(context: Context) : ShowcaseSeenStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun isSeen(hook: ShowcaseHookId): Boolean = prefs.getBoolean(key(hook), false)

    override suspend fun markSeen(hook: ShowcaseHookId) {
        prefs.edit { putBoolean(key(hook), true) }
    }

    override suspend fun clearAll() {
        prefs.edit { clear() }
    }

    private fun key(hook: ShowcaseHookId) = "seen_${hook.name}"

    private companion object {
        const val PREFS_NAME = "odo_showcase"
    }
}
