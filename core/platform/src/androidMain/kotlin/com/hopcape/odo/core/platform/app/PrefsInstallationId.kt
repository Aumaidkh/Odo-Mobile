package com.hopcape.odo.core.platform.app

import android.content.Context
import androidx.core.content.edit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [InstallationId] on `SharedPreferences` — one file, one string.
 *
 * Prefs rather than the database, for the same reason as `PrefsShowcaseSeenStore`: this is
 * device state the record is not made of, so there is no schema and nothing for an installed
 * build to migrate. It is also read from a WorkManager worker with no database open, and a
 * pref read cannot fail on a locked file.
 *
 * `by lazy` does the generate-once work under a lock, so two threads asking at the same time
 * on first launch get the same id rather than racing to write two.
 */
internal class PrefsInstallationId(context: Context) : InstallationId {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @OptIn(ExperimentalUuidApi::class)
    override val value: String by lazy {
        prefs.getString(KEY_INSTALL_ID, null) ?: Uuid.random().toString().also {
            prefs.edit { putString(KEY_INSTALL_ID, it) }
        }
    }

    private companion object {
        const val PREFS_NAME = "odo_install"
        const val KEY_INSTALL_ID = "install_id"
    }
}
