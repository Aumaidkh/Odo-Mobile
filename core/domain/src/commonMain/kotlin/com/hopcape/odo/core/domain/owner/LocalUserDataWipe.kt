package com.hopcape.odo.core.domain.owner

/**
 * Removes the signed-in owner's data from this device.
 *
 * Used by sign-out. Everything it clears is on the server, so this is not deletion in the
 * DPDP sense — it is forgetting a copy. Signing back in pulls it down again.
 *
 * **It exists because of account switching.** Rows are stamped with the owner who wrote
 * them; if A signs out and B signs in on the same phone, A's rows are still here, still
 * visible, and still carrying A's `owner_id` — which B's token cannot push, so every sync
 * pass would fail on them forever. Clearing on the way out is what keeps a shared device
 * from becoming a permanently broken one.
 *
 * A port in `:core:domain` because auth is what calls it and `:core:data` is what can do it,
 * and neither may depend on the other.
 */
fun interface LocalUserDataWipe {

    /**
     * Forget everything belonging to the owner, and reset sync so the next sign-in starts
     * clean.
     *
     * Device preferences — theme, units, notification choices — are deliberately kept: they
     * describe the phone, not the account, and someone signing back in has not asked for
     * their text size to change.
     */
    suspend fun wipe()
}
