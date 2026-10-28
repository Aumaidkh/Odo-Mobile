package com.hopcape.odo.core.platform.notification

import android.content.Context
import androidx.core.content.edit

/** [NudgeSentStore] on `SharedPreferences`, one boolean per nudge. */
internal class PrefsNudgeSentStore(context: Context) : NudgeSentStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun isSent(nudge: EngagementNudge): Boolean = prefs.getBoolean(key(nudge), false)

    override suspend fun markSent(nudge: EngagementNudge) {
        prefs.edit { putBoolean(key(nudge), true) }
    }

    private fun key(nudge: EngagementNudge) = "sent_${nudge.name}"

    private companion object {
        const val PREFS_NAME = "odo_nudges"
    }
}
