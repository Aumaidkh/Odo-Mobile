package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.sync.SyncGate
import com.hopcape.odo.core.sync.SyncVerdict
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
 * on the way past, so a run never starts with a token that dies mid-push.
 *
 * **A null token has two meanings and they are not the same.** Nobody signed in yet is an
 * ordinary state for an offline-first app, and retrying changes nothing until somebody does.
 * A session this install *holds* but could not get a usable token for right now is a
 * different thing entirely: a refresh that timed out, a secure store that would not open.
 * Both used to come back as a plain `false`, which the worker recorded as a finished run —
 * so a sign-in sync that asked a moment too early was simply lost, initial pull and all
 * (issue #312). [SessionStatusProvider] is what tells them apart, and it reads memory rather
 * than the network, so asking costs nothing.
 *
 * **This lives in `:core:data`, not in an auth or vendor module.** It needs three
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
    private val sessions: SessionStatusProvider,
    private val owners: CurrentOwnerProvider,
    private val adoption: OwnershipAdoption,
    private val clock: Clock = Clock.System,
) : SyncGate {

    override suspend fun evaluate(): SyncVerdict {
        if (tokens.currentAccessToken() == null) {
            return if (sessions.isSignedIn()) SyncVerdict.Unavailable(NO_TOKEN)
            else SyncVerdict.NoSession(NOT_SIGNED_IN)
        }
        adoption.adopt(realOwnerId = owners.currentOwnerId().value, now = clock.now())
        return SyncVerdict.Allowed
    }

    private companion object {
        const val NOT_SIGNED_IN = "not signed in"
        const val NO_TOKEN = "session held but no usable token"
    }
}
