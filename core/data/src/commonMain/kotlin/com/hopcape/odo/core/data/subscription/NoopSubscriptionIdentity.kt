package com.hopcape.odo.core.data.subscription

import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity

/**
 * The [SubscriptionIdentity] in force when no store is configured: there is no purchase to
 * attach to anyone.
 *
 * Bound here rather than left unbound so `:feature:auth` can call it unconditionally. Sign-in
 * should not know whether this build can sell anything, and a build with no billing module —
 * or no RevenueCat key — must not crash on the line that links an account to a subscription.
 *
 * `:infrastructure:billing` replaces it in one line of its Koin module.
 */
internal class NoopSubscriptionIdentity : SubscriptionIdentity {

    override fun identify(ownerId: OwnerId) = Unit

    override fun forget() = Unit
}
