package com.hopcape.odo.core.domain.entitlement

import kotlinx.coroutines.flow.Flow

/**
 * Entitlement decided by a person rather than by the store.
 *
 * Support grants a comp, an internal tester needs Pro, somebody is cut off for
 * abuse. None of that is something RevenueCat knows or should know, so it arrives
 * on its own port and is composed over the store's answer.
 *
 * A stream, like [EntitlementSource]: an override granted while the app is open
 * should take effect on the next sync rather than the next launch.
 */
interface EntitlementOverrides {

    /**
     * What has been decided for this owner, keyed by feature name.
     *
     * `true` grants, `false` revokes, and an absent key means nobody has decided —
     * which is not the same as a revoke. Empty is the normal case.
     */
    fun observe(): Flow<Map<String, Boolean>>

    companion object {
        /**
         * The whole plan, as opposed to a single capability.
         *
         * The only key in use today: there is one Pro plan, and granting somebody
         * "Pro" is what support actually does. Per-feature keys work already — the
         * map is keyed by name — and need no schema change when they arrive.
         */
        const val PLAN = "PRO"
    }
}
