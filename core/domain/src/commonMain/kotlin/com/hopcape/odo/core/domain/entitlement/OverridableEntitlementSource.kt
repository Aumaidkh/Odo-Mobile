package com.hopcape.odo.core.domain.entitlement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The store's answer, with a person's decision on top.
 *
 * In `:core:domain` rather than beside the adapters, because it is a composition of two
 * ports and nothing else — which is also what lets `:infrastructure:billing` use it
 * without depending on `:core:data`.
 *
 * Wraps rather than replaces [EntitlementSource]: what somebody paid for is still
 * RevenueCat's to say, and this only adds the cases the store cannot know about —
 * a comp, a goodwill grant, an internal tester, an account cut off for abuse.
 *
 * **The override wins in both directions**, and the revoke direction is the one
 * worth stating. A grant that could not be taken back would make every mistaken
 * grant permanent, and a revoke is also how support stops someone who is abusing
 * a subscription they genuinely hold. So `granted = false` beats a paying store
 * answer, exactly as `granted = true` beats a free one.
 *
 * Overrides are read from the local mirror, so this keeps working offline. That
 * matters more here than usual: an entitlement that disappears on a flaky
 * connection is a Pro owner looking at a paywall.
 */
class OverridableEntitlementSource(
    private val store: EntitlementSource,
    private val overrides: EntitlementOverrides,
) : EntitlementSource {

    override fun observe(): Flow<Entitlements> =
        combine(store.observe(), overrides.observe()) { fromStore, decided ->
            when (decided[EntitlementOverrides.PLAN]) {
                true -> Entitlements(Plan.PRO)
                false -> Entitlements(Plan.FREE)
                // Nobody has decided anything, which is not the same as a revoke.
                null -> fromStore
            }
        }

    /**
     * Only the store is asked again.
     *
     * The overrides come from the local mirror and are refreshed by the sync
     * engine, not by this call — asking the network here would put a second,
     * unscheduled reader on a table the sync pass already owns.
     */
    override suspend fun refresh() = store.refresh()
}
