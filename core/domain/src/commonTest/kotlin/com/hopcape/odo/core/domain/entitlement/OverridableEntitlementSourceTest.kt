package com.hopcape.odo.core.domain.entitlement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OverridableEntitlementSourceTest {

    private class FixedStore(private val plan: Plan) : EntitlementSource {
        override fun observe(): Flow<Entitlements> = flowOf(Entitlements(plan))
        override suspend fun refresh() = Unit
    }

    private class FixedOverrides(private val map: Map<String, Boolean>) : EntitlementOverrides {
        override fun observe(): Flow<Map<String, Boolean>> = flowOf(map)
    }

    private suspend fun planOf(store: Plan, overrides: Map<String, Boolean>): Plan =
        OverridableEntitlementSource(FixedStore(store), FixedOverrides(overrides)).observe().first().plan

    @Test
    fun `with no override the store decides`() = runTest {
        assertEquals(Plan.FREE, planOf(Plan.FREE, emptyMap()))
        assertEquals(Plan.PRO, planOf(Plan.PRO, emptyMap()))
    }

    /** A comp, an internal tester, a support goodwill. The store knows none of them. */
    @Test
    fun `a grant beats a free store answer`() = runTest {
        assertEquals(Plan.PRO, planOf(Plan.FREE, mapOf(EntitlementOverrides.PLAN to true)))
    }

    /**
     * The direction worth stating. Without it a mistaken grant would be permanent, and
     * there would be no way to stop somebody abusing a subscription they really hold.
     */
    @Test
    fun `a revoke beats a paying store answer`() = runTest {
        assertEquals(Plan.FREE, planOf(Plan.PRO, mapOf(EntitlementOverrides.PLAN to false)))
    }

    /**
     * An override for something else must not be read as an answer about the plan —
     * absent means nobody decided, which is not the same as a revoke.
     */
    @Test
    fun `an override for another feature leaves the plan alone`() = runTest {
        assertEquals(Plan.PRO, planOf(Plan.PRO, mapOf("SOMETHING_ELSE" to false)))
        assertEquals(Plan.FREE, planOf(Plan.FREE, mapOf("SOMETHING_ELSE" to true)))
    }
}
