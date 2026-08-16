package com.hopcape.odo.core.platform.showcase

import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import platform.Foundation.NSUserDefaults

/**
 * [ShowcaseSeenStore] on `NSUserDefaults` — the iOS mirror of `PrefsShowcaseSeenStore`.
 * Namespaced keys instead of a separate suite, the simplest thing that persists.
 */
internal class DefaultsShowcaseSeenStore : ShowcaseSeenStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun isSeen(hook: ShowcaseHookId): Boolean = defaults.boolForKey(key(hook))

    override suspend fun markSeen(hook: ShowcaseHookId) {
        defaults.setBool(true, key(hook))
    }

    override suspend fun clearAll() {
        ShowcaseHookId.entries.forEach { defaults.removeObjectForKey(key(it)) }
    }

    private fun key(hook: ShowcaseHookId) = "odo_showcase_seen_${hook.name}"
}
