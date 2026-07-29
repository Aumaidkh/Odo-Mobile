package com.hopcape.odo.feature.auth

import com.hopcape.odo.core.domain.owner.SessionStatusProvider

/**
 * MVP stub for [SessionStatusProvider]: there is no real auth yet, so the honest answer
 * is "signed out" — which is what makes onboarding offer the sign-in prompt on finish.
 *
 * Auth owns this binding (not onboarding) because sessions are auth's concern; onboarding
 * only asks the port. When Supabase phone auth lands it replaces this class and nothing
 * else moves — same shape as `LocalOwnerProvider` in `:feature:onboarding`.
 */
internal class LocalSessionStatus : SessionStatusProvider {
    override fun isSignedIn(): Boolean = false
}
