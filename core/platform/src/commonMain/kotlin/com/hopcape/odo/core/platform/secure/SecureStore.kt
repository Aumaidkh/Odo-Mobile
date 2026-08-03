package com.hopcape.odo.core.platform.secure

/**
 * Small secrets, kept where the operating system can protect them.
 *
 * This exists for exactly one thing today: the Supabase session. An access token is a bearer
 * credential — whoever holds it *is* the owner until it expires — and a refresh token renews
 * it indefinitely, so neither belongs in `SharedPreferences` or a SQLDelight column where a
 * rooted device or a backup extraction hands them over in plain text.
 *
 * Values are strings because that is what tokens are. This is not a cache and not a
 * key-value store for general use: keep it to credentials, because every platform's secure
 * storage is slow compared to a file, and on iOS entries survive an app uninstall.
 *
 * Reads and writes are `suspend` — the Android implementation touches the Keystore, which
 * does real cryptographic work and must not run on the main thread.
 */
interface SecureStore {

    /** Store [value] under [key], replacing anything already there. */
    suspend fun put(key: String, value: String)

    /** The stored value, or null if there is none (or it could not be decrypted). */
    suspend fun get(key: String): String?

    /** Forget one entry. Used on sign-out. */
    suspend fun remove(key: String)

    /** Forget everything this app stored. Used on account deletion. */
    suspend fun clear()

    companion object {
        /** The Supabase access token — short-lived, sent as the bearer on every request. */
        const val KEY_ACCESS_TOKEN = "supabase_access_token"

        /** The refresh token — long-lived, and the reason this store is encrypted. */
        const val KEY_REFRESH_TOKEN = "supabase_refresh_token"

        /** When the access token stops working, ISO-8601. Not a secret, but it lives with them. */
        const val KEY_EXPIRES_AT = "supabase_expires_at"

        /** The signed-in user's id, which becomes `owner_id` on every row they own. */
        const val KEY_USER_ID = "supabase_user_id"
    }
}
