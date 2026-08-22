package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.domain.auth.AccessTokenProvider
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.sync.SyncVerdict
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * The gate's job is to tell three states apart, not two.
 *
 * A null token used to mean one thing — refuse — and the worker recorded every refusal as a
 * finished run. So a sign-in sync that reached for a token a moment too early was dropped
 * outright, taking the install's first pull with it (issue #312). "Nobody is signed in" and
 * "we hold a session but could not get a token just now" need different answers.
 */
class SessionSyncGateTest {

    @Test
    fun `a usable token allows the run and adopts pre-auth rows`() = runTest {
        val adoption = RecordingAdoption()
        val gate = gate(token = "jwt", signedIn = true, adoption = adoption)

        assertEquals(SyncVerdict.Allowed, gate.evaluate())
        assertEquals(listOf(OWNER), adoption.adopted)
    }

    @Test
    fun `nobody signed in is not retryable`() = runTest {
        // Signing in is what changes this, and the app is fully usable offline meanwhile.
        val gate = gate(token = null, signedIn = false)

        assertIs<SyncVerdict.NoSession>(gate.evaluate())
    }

    @Test
    fun `a session we cannot get a token for is retryable`() = runTest {
        // The regression. A refresh that timed out, a secure store that would not open —
        // neither is evidence that this install has no account, and recording the run as
        // done is what loses it.
        val gate = gate(token = null, signedIn = true)

        assertIs<SyncVerdict.Unavailable>(gate.evaluate())
    }

    @Test
    fun `a refused run never runs adoption`() = runTest {
        // Adoption re-stamps rows and puts them in the outbox. Doing that for a run that is
        // about to be refused would mark rows PENDING with nothing able to push them.
        val adoption = RecordingAdoption()

        gate(token = null, signedIn = true, adoption = adoption).evaluate()

        assertEquals(emptyList(), adoption.adopted)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun gate(
        token: String?,
        signedIn: Boolean,
        adoption: RecordingAdoption = RecordingAdoption(),
    ) = SessionSyncGate(
        tokens = AccessTokenProvider { token },
        sessions = object : SessionStatusProvider {
            override fun isSignedIn() = signedIn
        },
        owners = CurrentOwnerProvider { OwnerId(OWNER) },
        adoption = adoption,
    )

    private class RecordingAdoption : OwnershipAdoption {
        val adopted = mutableListOf<String>()
        override suspend fun adopt(realOwnerId: String, now: Instant) {
            adopted += realOwnerId
        }
    }

    private companion object {
        const val OWNER = "5b28c012-545f-447d-9a85-920084f68246"
    }
}
