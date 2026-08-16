package com.hopcape.odo.core.domain.showcase

/**
 * The device-local record of which coach marks have been seen (#225).
 *
 * Deliberately not part of the synced record: "I have seen this tip" belongs to a phone,
 * not to an account. Signing in on a new phone teaches the app again, which is right —
 * it is a new phone. The implementation is a `SharedPreferences` file on Android
 * (`PrefsShowcaseSeenStore` in `:core:platform`), not a database table: no schema, no
 * migration, nothing for a Play upgrade to trip over.
 *
 * Seen is written on dismiss and on act-on alike — someone who read a coach mark and
 * tapped it away has seen it. Recording only completions is how a tip comes back forever.
 */
interface ShowcaseSeenStore {

    suspend fun isSeen(hook: ShowcaseHookId): Boolean

    suspend fun markSeen(hook: ShowcaseHookId)

    /** The "Show me around again" reset (#234) and the privacy eraser both land here. */
    suspend fun clearAll()
}
