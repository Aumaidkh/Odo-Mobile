package com.hopcape.odo.core.domain.owner

import com.hopcape.odo.core.domain.owner.model.OwnerId

/**
 * Port resolving the identity of the owner the current session acts as.
 *
 * The unstable "who is signed in?" dependency behind a stable contract
 * (Ports & Adapters). M1 has no auth, so a stub returns a fixed local owner id
 * for offline use; when real auth/session lands it implements this same port and
 * the binding swaps — callers (ViewModels, use-case wiring) stay untouched.
 *
 * The owner id minted client-side here is an offline placeholder; the server
 * stamps the authoritative `owner_id` on sync (DB_SCHEMA — clients never spoof it).
 */
fun interface CurrentOwnerProvider {
    fun currentOwnerId(): OwnerId
}
