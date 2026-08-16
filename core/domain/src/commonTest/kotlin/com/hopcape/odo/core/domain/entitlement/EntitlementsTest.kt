package com.hopcape.odo.core.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsTest {

    @Test
    fun freePlanHoldsThreeDocumentsAndThreeScans() {
        val free = Entitlements(Plan.FREE)

        assertTrue(free.has(ProFeature.DOCUMENTS))
        assertEquals(3, free.quotaFor(ProFeature.DOCUMENTS).cap)
        assertEquals(3, free.quotaFor(ProFeature.BILL_SCANS).cap)
    }

    @Test
    fun freePlanDoesNotGetTheBreakdown() {
        val free = Entitlements(Plan.FREE)

        assertFalse(free.has(ProFeature.HEALTH_BREAKDOWN))
    }

    /**
     * The counted features are the reason `has` is not the question a caller should ask about
     * them. It answers "does the plan grant this at all", and for a capped feature that is
     * true from the first use to the last — only `quotaFor(...).allowsAnother(used)` knows
     * whether there is one left. A gate written with `has` would hand a capped feature over
     * on the free plan, which is what this states so the next person does not.
     */
    @Test
    fun freePlanGrantsCountedFeaturesButNotWithoutLimit() {
        val free = Entitlements(Plan.FREE)

        listOf(
            ProFeature.RECORD_EXPORT,
            ProFeature.SMART_REFUEL_DETECT,
            ProFeature.DOCUMENTS,
            ProFeature.BILL_SCANS,
        ).forEach { feature ->
            val quota = free.quotaFor(feature)
            assertTrue(free.has(feature), "$feature is granted in some amount")
            val cap = quota.cap
            assertTrue(cap != null && cap > 0, "$feature is capped on the free plan")
            assertTrue(quota.allowsAnother(used = 0), "$feature allows the first")
            assertFalse(quota.allowsAnother(used = cap), "$feature stops at its cap")
        }
    }

    @Test
    fun proGetsEverything() {
        val pro = Entitlements(Plan.PRO)

        ProFeature.entries.forEach { assertTrue(pro.has(it), "pro should have $it") }
        assertEquals(null, pro.quotaFor(ProFeature.DOCUMENTS).cap, "pro has no cap to show")
    }

    @Test
    fun unknownIsFree() {
        assertEquals(Plan.FREE, Entitlements.Unknown.plan)
        assertFalse(
            Entitlements.Unknown.has(ProFeature.HEALTH_BREAKDOWN),
            "an entitlement the app cannot prove must not unlock Pro",
        )
    }
}
