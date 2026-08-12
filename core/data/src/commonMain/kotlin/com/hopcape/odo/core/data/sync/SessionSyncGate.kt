package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.sync.SyncGate
import kotlin.time.Clock

/**
 * The gate the engine asks before every run: is there a usable session, and does this
 * install's data belong to it yet?
 *
 * Both, in that order, because the second is meaningless without the first. Being signed in
 * is what makes `owner_id` knowable; adoption is what makes the rows written *before* that
 * pushable. A run that skipped adoption would push nothing and still look healthy.
 *
 * Asking for a token rather than reading a boolean is deliberate — it refreshes a stale one
 * on the way past, so a run never starts with a token that dies mid-push. A null answer
 * means no session, which is an ordinary state for an offline-first app, not a failure.
 *
 * **This lives in `:core:data`, not in an auth or vendor module.** It needs two
 * `:core:domain` ports and the adoption step that sits here; nothing about it is
 * Supabase-specific, and putting it in `:feature:auth` would mean the sync engine depended
 * on a feature.
 *
 * Adoption runs on **every** pass rather than once behind a flag. It matches on the
 * placeholder id, so after the first pass it finds nothing and costs an empty transaction —
 * and a flag that could be wrong is a worse failure than a query that is always right.
 */
internal class SessionSyncGate(
    private val tokens: AccessTokenProvider,
    private val owners: CurrentOwnerProvider,
    private val adoption: OwnershipAdoption,
    private val clock: Clock = Clock.System,
) : SyncGate {

    override suspend fun canSync(): Boolean {
        tokens.currentAccessToken() ?: return false
        adoption.adopt(realOwnerId = owners.currentOwnerId().value, now = clock.now())
        return true
    }
}
